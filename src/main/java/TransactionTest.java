import info.orestes.client.OrestesClient;
import info.orestes.client.TransactionClient;
import info.orestes.common.error.ObjectOutOfDate;
import info.orestes.common.error.TransactionAborted;
import info.orestes.common.error.TransactionUnavailable;
import info.orestes.common.typesystem.*;
import info.orestes.pluggable.types.data.OObject;
import info.orestes.rest.conversion.ClassFieldSpecification;
import info.orestes.rest.conversion.ClassSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * A test case that uses bank accounts and money transfers
 * to show the usage of baqend and it's transaction api in particular.
 */
public class TransactionTest {
    public static final Bucket TEST_BUCKET = new Bucket("test.bucket.Value");

    private final OrestesClient client;
    private final UserInfo rootUser;
    private final Random rnd;

    private List<ObjectRef> accountReferences;

    public TransactionTest() {
        // Create a deterministic random generator
        rnd = new Random(213456);
        // Connect a client to the running baqend server using api version v1
        client = new OrestesClient("http://localhost:8080/v1");

        // Set the root user with its credentials
        rootUser = client.login(new UserLogin("root", "root")).getSignedUserInfo();

        /*
         * A class specification with field of various types (this test only uses the amout field).
         * However it is way easier to create and update the
         * database schema using the web views of the baqend server.
         */
        ClassSpecification s = new ClassSpecification(TEST_BUCKET, BucketAcl.createDefault(),
                new ClassFieldSpecification("ref", TEST_BUCKET),
                new ClassFieldSpecification("amount", Bucket.INTEGER),
                new ClassFieldSpecification("name", Bucket.STRING),
                new ClassFieldSpecification("list", Bucket.LIST, Bucket.STRING),
                new ClassFieldSpecification("date", Bucket.DATETIME),
                new ClassFieldSpecification("geo", Bucket.GEOPOINT));

        // Add the schema for the test bucket on the server side.
        client.getSchema().add(s, rootUser);
    }

    /**
     * Fills the database with the specified number of bank accounts.
     *
     * @param numAccounts The number of bank accounts.
     */
    public void initEconomy(int numAccounts) {
        // delete all objects from the test bucket
        client.truncateBucket(TEST_BUCKET, rootUser);
        accountReferences = new ArrayList<>(numAccounts);

        for (int i = 0; i < numAccounts; i++) {
            OObject account = createBankAccount(100);
            // Insert a single account into the database (non transactional)
            client.insert(account);
            accountReferences.add(account.getRef());
        }
    }

    /**
     * Creates a new account with the given initial balance (without inserting it into the database).
     *
     * @param initialBalance The intial balance of the account.
     * @return The account object.
     */
    private OObject createBankAccount(int initialBalance) {
        // Generate a new unique object reference
        ObjectRef newRef = ObjectRef.create(TEST_BUCKET);
        // Instantiate an object for the new references
        OObject account = client.getSchema().getClass(newRef.getBucket()).newInstance(newRef, ObjectAcl.createDefault());
        // Set an initial balance
        account.setValue("amount", initialBalance);
        return account;
    }

    /**
     * Transfers a random amount between two random accounts.
     *
     * @return true if the transaction was committed successfully, false otherwise.
     */
    public boolean doRandomMoneyTransfer() {
        // Starts a transaction and returns a client that handles this single transaction
        TransactionClient transaction = client.beginTransactionWithClient();

        // load two random accounts in the transaction
        ObjectRef ref1 = Utils.getRandomElement(accountReferences, rnd);
        ObjectRef ref2 = Utils.getRandomElement(accountReferences, rnd);
        OObject account1 = transaction.load(ref1);
        OObject account2 = transaction.load(ref2);

        int transferAmount = rnd.nextInt(100);
        // transfer the money
        int newAmount2 = (int) account1.getValue("amount") - transferAmount;
        int newAmount1 = (int) account2.getValue("amount") + transferAmount;
        account1.setValue("amount", newAmount1);
        account2.setValue("amount", newAmount2);

        // save the accounts
        transaction.update(account1);
        transaction.update(account2);

        // commit the transaction
        try {
            transaction.commit();
            return true;
        } catch (ObjectOutOfDate objectOutOfDate) {
            // Transaction was aborted due to concurrency
        } catch (TransactionAborted transactionAborted) {
            // Transaction was aborted because locks could not be acquired (timeout)
        } catch (TransactionUnavailable transactionUnavailable) {
            // transaction was aborted due to unavailable components (like redis)
        }
        return false;
    }

    /**
     * Queries the summed balance of all the bank accounts.
     *
     * @return The summed balance of all the bank accounts.
     */
    public long queryOverallBalance() {
        // if the transaction is aborted retry at most 5 times.
        int retries = 0;
        trying:
        while (retries < 5) {
            // start transaction
            TransactionClient transaction = client.beginTransactionWithClient();
            // load all accounts
            Stream<OObject> accounts = transaction.loadAllObjects(accountReferences.stream());

            // sum up balances
            Stream<Long> balances = accounts.map(account -> account.getValue("balance"));
            Long balance = balances.reduce(0L, (a, b) -> a + b);

            //commit transaction
            try {
                transaction.commit();
            } catch (ObjectOutOfDate | TransactionUnavailable | TransactionAborted error) {
                retries++;
                continue trying;
            }

            return balance;
        }

        return -1;
    }

    public static void main(String[] args) {
        int runs = 100;
        TransactionTest test = new TransactionTest();

        // Init the economy with 100 accounts
        test.initEconomy(10_000);

        // calculate overall balance
        long overallBalance = test.queryOverallBalance();
        System.out.println(overallBalance);

        // execute some transfers in parallel
        Stream<Integer> parallel = Stream.generate(() -> 0).limit(runs).parallel();
        long successes = parallel.filter(ignored -> test.doRandomMoneyTransfer()).count();
        System.out.println("Successful money transfers: " + successes);
        System.out.println("Aborted money transfers: " + (runs - successes));

        // calculate overall balance again (should not have been changed)
        overallBalance = test.queryOverallBalance();
        System.out.println(overallBalance);
    }

}

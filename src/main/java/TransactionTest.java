import info.orestes.client.OrestesClient;
import info.orestes.client.TransactionClient;
import info.orestes.common.error.ObjectOutOfDate;
import info.orestes.common.error.TransactionAborted;
import info.orestes.common.error.TransactionUnavailable;
import info.orestes.common.typesystem.*;
import info.orestes.pluggable.types.data.DBClassField;
import info.orestes.pluggable.types.data.OObject;
import info.orestes.rest.conversion.ClassFieldSpecification;
import info.orestes.rest.conversion.ClassSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A test case that uses bank accounts and money transfers to show the usage of baqend and it's transaction api in
 * particular.
 */
public class TransactionTest {
    public static final Bucket TEST_BUCKET = new Bucket("test.bucket.Value");
    private final DBClassField BALANCE_CLASS_FIELD;

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
                // CAUTION! Even though the type is named integer you have to store and load long values!!!
                new ClassFieldSpecification("balance", Bucket.INTEGER), new ClassFieldSpecification("name", Bucket.STRING),
                new ClassFieldSpecification("list", Bucket.LIST, Bucket.STRING),
                new ClassFieldSpecification("date", Bucket.DATETIME), new ClassFieldSpecification("geo", Bucket.GEOPOINT));

        // Add the schema for the test bucket on the server side.
        client.getSchema().add(s, rootUser);

        // Get class field for the account balance (needed for partial updates)
        BALANCE_CLASS_FIELD = client.getSchema().getClass(TEST_BUCKET).getField("balance");
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
    private OObject createBankAccount(long initialBalance) {
        // Generate a new unique object reference
        ObjectRef newRef = ObjectRef.create(TEST_BUCKET);
        // Instantiate an object for the new references
        OObject account = client.getSchema()
                .getClass(newRef.getBucket())
                .newInstance(newRef, ObjectAcl.createDefault());
        // Set an initial balance
        account.setValue("balance", initialBalance);
        return account;
    }

    /**
     * Transfers a random amount between two random accounts.
     *
     * @return true if the transaction was committed successfully, false otherwise.
     */
    public CompletableFuture<Boolean> doRandomMoneyTransfer() {
        // Starts a transaction and returns a client that handles this single transaction
        // This example uses the async api, there is also a much lighter synchronous api.
        return client.beginTransactionWithAsyncClient().thenCompose(transaction -> {

                    ObjectRef ref1 = Utils.getRandomElement(accountReferences, rnd);
                    ObjectRef ref2 = Utils.getRandomElement(accountReferences, rnd);

                    // retry method if the random accounts equal.
                    if (Objects.equals(ref1, ref2)) {
                        return doRandomMoneyTransfer();
                    }

                    // load two random accounts in the transaction
                    CompletableFuture<OObject> account1Future = transaction.loadAsync(ref1);
                    CompletableFuture<OObject> account2Future = transaction.loadAsync(ref2);

                    return CompletableFuture.allOf(account1Future, account2Future).thenCompose(ignored -> {
                        OObject account1 = account1Future.join();
                        OObject account2 = account2Future.join();

                        int transferAmount = rnd.nextInt(100);
                        // transfer the money
                        long newBalance2 = (long) account1.getValue("balance") - transferAmount;
                        long newBalance1 = (long) account2.getValue("balance") + transferAmount;
                        account1.setValue("balance", newBalance1);
                        account2.setValue("balance", newBalance2);

                        // save the accounts
                        transaction.update(account1);
                        transaction.update(account2);

                /*
                 * commit the transaction:
                 * Error ObjectOutOfDate indicates: Transaction was aborted due to concurrency
                 * Error TransactionAborted indicates: Transaction was aborted because locks could not be acquired (timeout)
                 * Error TransactionUnavailable indicates: transaction was aborted due to unavailable components (like redis)
                 */
                        return transaction.commitAsync().handle((result, error) -> error == null);
                    });
                }

        );
    }

    /**
     * Transfers a random amount between two random accounts using partial updates. This implementation is much faster
     * than the read-modify-write version and produces no conflicts!
     *
     * @return true if the transaction was committed successfully, false otherwise.
     */
    public CompletableFuture<Boolean> doMoneyTransferPartialUpdate() {
        // Starts a transaction and returns a client that handles this single transaction
        return client.beginTransactionWithAsyncClient().thenCompose(transaction -> {

            // load two random accounts in the transaction
            ObjectRef ref1 = Utils.getRandomElement(accountReferences, rnd);
            ObjectRef ref2 = Utils.getRandomElement(accountReferences, rnd);

            // retry method if the random accounts equal.
            if (Objects.equals(ref1, ref2)) {
                return doMoneyTransferPartialUpdate();
            }

            long transferAmount = rnd.nextInt(100);

            // increment the balance of account one by the transfer amount using a partial update
            transaction.partialUpdate(ref1,
                    new UpdateOperation("balance", UpdateOperation.Operation.inc, BALANCE_CLASS_FIELD, transferAmount));
            // decrement for account two. With partial updates objects do not have to be loaded and the updates do not create conflicts.
            transaction.partialUpdate(ref2,
                    new UpdateOperation("balance", UpdateOperation.Operation.dec, BALANCE_CLASS_FIELD, transferAmount));

            return transaction.commitAsync().handle((result, error) -> error == null);
        });
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
            Stream<Long> balances = accounts.map(account -> {
                Long amount = 0L;
                if (account != null) {
                    amount = account.getValue("balance");
                    if (amount == null) {
                        amount = 0L;
                    }
                }
                return amount;
            });
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

        int runs = 1000;
        TransactionTest test = new TransactionTest();

        // Init the economy with 100 accounts
        int numAccounts = 10_000;
        test.initEconomy(numAccounts);
        System.out.println("DB initialized with " + numAccounts + " bank accounts");

        // calculate overall balance
        long overallBalance = test.queryOverallBalance();
        System.out.println("Intial overall balance: " + overallBalance);
        System.out.println();

        // execute some transfers in parallel
        System.out.println("Simple read-modify-write transfers (" + runs + " runs)");
        long successes = Utils.timed(() -> IntStream.range(0, runs)
                .mapToObj(ignored -> test.doRandomMoneyTransfer())
                .collect(Collectors.toList())
                .stream()
                .filter(CompletableFuture::join)
                .count());
        System.out.println("Successful money transfers: " + successes);
        System.out.println("Aborted money transfers: " + (runs - successes));

        // calculate overall balance again (should not have been changed)
        overallBalance = test.queryOverallBalance();
        System.out.println("Final overall balance: " + overallBalance);
        System.out.println();

        // execute some transfers in parallel
        System.out.println("Partial update transfers (" + runs + " runs)");
        successes = Utils.timed(() -> IntStream.range(0, runs)
                .mapToObj(ignored -> test.doMoneyTransferPartialUpdate())
                .collect(Collectors.toList())
                .stream()
                .filter(CompletableFuture::join)
                .count());
        System.out.println("Successful money transfers: " + successes);
        System.out.println("Aborted money transfers: " + (runs - successes));

        // calculate overall balance again (should not have been changed)
        overallBalance = test.queryOverallBalance();
        System.out.println("Final overall balance: " + overallBalance);
        System.out.println();

        System.out.println(
                "Please note that the time information is not an actual benchmark.");

        System.exit(0);
    }
}


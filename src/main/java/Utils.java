import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Created by erikwitt on 04.04.16.
 */
public class Utils {

    /**
     * Returns a random element from the list.
     */
    public static <T> T getRandomElement(List<T> elems, Random rnd) {
        if (elems.isEmpty()) {
            return null;
        }
        return elems.get(rnd.nextInt(elems.size()));
    }

    /**
     * Returns the result of the call to the supplier and print the measured time.
     *
     * @param func The function to execute.
     * @param <T>  The return type of the function.
     * @return The result of the function call.
     */
    public static <T> T timed(Supplier<T> func) {
        long start = System.currentTimeMillis();
        T result = func.get();
        System.out.println("Time (in ms): " + (System.currentTimeMillis() - start));
        return result;
    }

}

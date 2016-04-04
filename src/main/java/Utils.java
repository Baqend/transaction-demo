import java.util.List;
import java.util.Random;

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

}

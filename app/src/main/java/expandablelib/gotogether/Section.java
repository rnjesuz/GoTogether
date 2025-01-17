package expandablelib.gotogether;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mertsimsek on 28/07/2017.
 */

public class Section<P, C> {
    public boolean expanded;
    public boolean user;
    public P parent;
    public List<C> children;

    public Section() {
        children = new ArrayList<>();
        expanded = false;
        user = false;
    }
}

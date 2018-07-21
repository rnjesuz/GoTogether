package app.gotogether;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import expandablelib.gotogether.ExpandCollapseListener;
import expandablelib.gotogether.ExpandableLayout;
import expandablelib.gotogether.Section;

public class CreateEvent2Activity extends AppCompatActivity {

    private String parents = "Do you volunteer as a Driver?";
    private boolean isDriver = false;
    private int emptySeats = -1; // -1 = no car. >0 = how many empty seats

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event2);

    }

}

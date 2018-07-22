package app.gotogether;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.List;

import expandablelib.gotogether.ExpandCollapseListener;
import expandablelib.gotogether.ExpandableLayout;
import expandablelib.gotogether.Section;

public class CreateEvent2Activity extends AppCompatActivity {

    private String parents = "Do you volunteer as a Driver?";
    private boolean isDriver = true;
    private int emptySeats = -1; // -1 = no car. >0 = how many empty seats
    int PLACE_AUTOCOMPLETE_REQUEST_CODE = 1;
    private String destination = null;
    private String start = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event2);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_close); // change the return to parent button
    }

    public void DestinationAutoComplete(View view){

        try {
            Intent intent =
                    new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY)
                            .build(this);
            startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE);
        } catch (GooglePlayServicesRepairableException e) {
            // TODO: Handle the error.
        } catch (GooglePlayServicesNotAvailableException e) {
            // TODO: Handle the error.
        }


    }

    public void StartAutoComplete(View view){

        try {
            Intent intent =
                    new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY)
                            .build(this);
            startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE);
        } catch (GooglePlayServicesRepairableException e) {
            // TODO: Handle the error.
        } catch (GooglePlayServicesNotAvailableException e) {
            // TODO: Handle the error.
        }

    }

    public void DriverVolunteer(View view){
        ConstraintLayout seatQuestion = (ConstraintLayout) findViewById(R.id.rl4);
        TextView driverQuestion = (TextView) findViewById(R.id.driver_question_tv);
        seatQuestion.requestLayout();
        if(isDriver) {
            driverQuestion.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.checkbox_outlined, 0);
            slideUp(seatQuestion);
            isDriver = false;
        } else {
            driverQuestion.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.checkbox_fill, 0);
            slideDown(seatQuestion);
            isDriver = true;
        }
    }

    private void slideUp(ConstraintLayout view) {
        view.animate()
                .translationY(-view.getHeight()/2)
                .alpha(0.0f)
                .setDuration(1000)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        view.setVisibility(View.INVISIBLE);
                    }
                });
    }

    private void slideDown(ConstraintLayout view) {
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0.0f);

        int bottom = view.getTop();
        int top = view.getBottom();
        // Start the animation
        view.animate()
                .translationY(bottom-top+(view.getHeight()))
                .alpha(1.0f)
                .setDuration(1000)
                .setListener(null);
    }


    /**
     Finalize event creation.
     Validate activity fields.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void concludeCreation(View view) {
        // Has destination?
        if (destination == null) {
            // Send error message
            createTextPopUpWindow("The event's destination cannot be empty.");
            return;
        }
        // Has pick-up location?
        if (start == null) {
            // Send error message
            createTextPopUpWindow("Your pick-up location cannot be empty.");
            return;
        }
        // Volunteered as driver?
        EditText seatsText = findViewById(R.id.seatNumberEdit);
        boolean unspecifiedSeats = TextUtils.isEmpty(seatsText.getText());
        if (isDriver) {
            if (unspecifiedSeats) {
                // Didn't specify seats, use Hint value
                emptySeats = Integer.parseInt(seatsText.getHint().toString());
            } else {
                // Use inputted values
                emptySeats = Integer.parseInt(seatsText.getText().toString());
            }
        }
        // Confirm creation
        createConfirmationPopUpWindow();
    }

    private void createConfirmationPopUpWindow() {
        RelativeLayout cConstraintLayout = (RelativeLayout) findViewById(R.id.createEventLayout);
        PopupWindow cPopupWindow;
        // Initialize a new instance of LayoutInflater service
        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);

        // Inflate the custom layout/view
        View customView = inflater.inflate(R.layout.confirmation_pop_up_window, null);

        // Initialize a new instance of popup window
        cPopupWindow = new PopupWindow(
                customView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        // Set an elevation value for popup window
        // Call requires API level 21
        if (Build.VERSION.SDK_INT >= 21) {
            cPopupWindow.setElevation(5.0f);
        }

        // Get a reference for the custom view text
        TextView messageText = (TextView) customView.findViewById(R.id.tv);
        // Set text
        if (isDriver) {
            String htmlMsg = "<b>Event destination: </b><br />" + destination + "<br /><b>Your pick-up location: </b><br />" + start + "<br /><b>Driver?</b><br />Yes<br /><b>Available seats: </b><br />" + emptySeats;
            Spanned spanned = Html.fromHtml(htmlMsg);
            SpannableString spannableString = new SpannableString(spanned);
            messageText.setText(spannableString);
        } else {
            String htmlMsg = "<b>Event destination: </b><br />" + destination + "<br /><b>Your pick-up location: </b><br />" + start + "<br /><b>Driver?</b><br />No";
            messageText.setText(Html.fromHtml(htmlMsg));
        }

        // Get a reference for the custom view confirm button
        Button confirmButton = (Button) customView.findViewById(R.id.ib_confirm);
        // Set a click listener for the popup window confirm button
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Restore activity to opaque
                ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
                clearDim(root);
                // Dismiss the popup window
                cPopupWindow.dismiss();
                // Confirm Event
                CreateEvent2Activity.this.confirmEvent();

            }
        });

        // Get a reference for the custom view cancel button
        Button cancelButton = (Button) customView.findViewById(R.id.ib_cancel);
        // Set a click listener for the popup window cancel button
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Restore activity to opaque
                ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
                clearDim(root);
                // Dismiss the popup window
                cPopupWindow.dismiss();
            }
        });
        // Detect a click outside the window - Dismiss is the default behaviour of outside click
        cPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
                clearDim(root);
            }
        });

        // Finally, show the popup window at the center location of root relative layout
        cPopupWindow.showAtLocation(cConstraintLayout, Gravity.CENTER, 0, 0);

        // Dim the activity
        ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
        applyDim(root, 0.8f);
    }

    public void createTextPopUpWindow(String message) {
        RelativeLayout tConstraintLayout = (RelativeLayout) findViewById(R.id.createEventLayout);
        PopupWindow tPopupWindow;
        // Initialize a new instance of LayoutInflater service
        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);

        // Inflate the custom layout/view
        View customView = inflater.inflate(R.layout.simple_text_pop_up_window, null);

        // Initialize a new instance of popup window
        tPopupWindow = new PopupWindow(
                customView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        // Set an elevation value for popup window
        // Call requires API level 21
        if (Build.VERSION.SDK_INT >= 21) {
            tPopupWindow.setElevation(5.0f);
        }

        // Get a reference for the custom view text
        TextView messageText = (TextView) customView.findViewById(R.id.tv);

        // Set text
        messageText.setText(message);

        // Get a reference for the custom view close button
        ImageButton closeButton = (ImageButton) customView.findViewById(R.id.ib_close);

        // Set a click listener for the popup window close button
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Restore activity to opaque
                ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
                clearDim(root);
                // Dismiss the popup window
                tPopupWindow.dismiss();
            }
        });

        // Detect a click outside the window - Dismiss is the default behaviour of outside click
        tPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
                clearDim(root);
            }
        });

        // Finally, show the popup window at the center location of root relative layout
        tPopupWindow.showAtLocation(tConstraintLayout, Gravity.CENTER, 0, 0);

        // Dim the activity
        ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
        applyDim(root, 0.8f);
    }

    /** Apply dim to the activity */
    public void applyDim(@NonNull ViewGroup parent, float dimAmount) {
        Drawable dim = new ColorDrawable(Color.BLACK);
        dim.setBounds(0, 0, parent.getWidth(), parent.getHeight());
        dim.setAlpha((int) (255 * dimAmount));

        ViewGroupOverlay overlay = parent.getOverlay();
        overlay.add(dim);
    }

    /** Clear dim from the activity */
    public void clearDim(@NonNull ViewGroup parent) {
        ViewGroupOverlay overlay = parent.getOverlay();
        overlay.clear();
    }

    public void confirmEvent() {
        Log.d("Confirmation", "Yup");
        //Talk with server
        //TODO send info. receive token
        //TODO make pop-up to confirm input

        //Launch new activity
        // TODO do split? if finalized : if not finalized - different activities based on each
        Intent intent = new Intent(CreateEvent2Activity.this, EventActivity.class);
        // Destination Bundle
        Bundle destinationBundle = new Bundle();
        LatLng destinationLatLng = getLocationFromAddress(getApplicationContext(), destination);
        destinationBundle.putParcelable("destinationLatLng", destinationLatLng);
        destinationBundle.putString("destinationAddress", destination);
        intent.putExtra("Destination", destinationBundle);
        // User Bundle
        Bundle userBundle = new Bundle();
        LatLng startLatLng = getLocationFromAddress(getApplicationContext(), start);
        User user;
        if (isDriver) {
            user = new User("Ricardo", start, startLatLng, isDriver, emptySeats);
        } else {
            user = new User("Ricardo", start, startLatLng);
        }
        userBundle.putParcelable("User", user);
        intent.putExtra("Participants", userBundle);
        // Start
        startActivity(intent);
    }

    /** Get latitude and longitude from the address*/
    public LatLng getLocationFromAddress(Context context, String strAddress) {

        Geocoder coder = new Geocoder(context);
        List<Address> address;
        LatLng p1 = null;

        try {
            // May throw an IOException
            address = coder.getFromLocationName(strAddress, 5);
            //TODO resolver caso do oceano, ou propriedade privada sem estrada -> Nenhum emdereço será retornado
            if (address == null) {
                return null;
            }

            Address location = address.get(0);
            p1 = new LatLng(location.getLatitude(), location.getLongitude());

        } catch (IOException ex) {

            ex.printStackTrace();
        }

        return p1;
    }
}

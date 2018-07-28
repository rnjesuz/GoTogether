package app.gotogether;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static app.gotogether.R.layout.event_layout;

class EventArrayAdapter extends ArrayAdapter<Event> {

    private Context context;
    private List<Event> eventList;

    // Constructor
    public EventArrayAdapter(@NonNull Context context, int resource, @NonNull ArrayList<Event> objects) {
        super(context, resource, objects);

        this.context = context;
        this.eventList = objects;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public View getView(int position, View convertView, ViewGroup parent) {

        //get the property we are displaying
        Event event = eventList.get(position);

        //get the inflater and inflate the XML layout for each item
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
        assert inflater != null;
        View view = inflater.inflate(event_layout, null);

        TextView title = (TextView) view.findViewById(R.id.titleView);
        TextView destination = (TextView) view.findViewById(R.id.destinationView);
        TextView participants = (TextView) view.findViewById(R.id.participantsView);
        ImageView image = (ImageView) view.findViewById(R.id.eventImg);

        //TODO
        title.setText(event.getTitle());

        //TODO
        destination.setText(event.getDestination());

        //TODO
        participants.setText(String.format("%s participants", Integer.toString(event.getParticipants())));

        //display trimmed excerpt for description
        /*int descriptionLength = property.getDescription().length();
        if(descriptionLength >= 100){
            String descriptionTrim = property.getDescription().substring(0, 100) + "...";
            description.setText(descriptionTrim);
        }else{
            description.setText(property.getDescription());
        }*/

        //get the image associated with this property
        int imageID = context.getResources().getIdentifier(event.getImage(), "mipmap", context.getPackageName());
        image.setImageResource(imageID);

        return view;
    }
}

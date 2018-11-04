package app.gotogether;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class DriverInEventAdapter extends RecyclerView.Adapter<DriverInEventAdapter.ViewHolder> {
    private DriverListener mListener;
    private ArrayList<String> mDriversUsername;
    private ArrayList<ArrayList<String>> mRidersUsername;
    private Context context;
    private int groupCounter = 1;

    public DriverInEventAdapter(ArrayList<String> driverItems, ArrayList<ArrayList<String>> ridersItems, DriverListener listener, Context context) {
        mDriversUsername = driverItems;
        mRidersUsername = ridersItems;
        mListener = listener;
        this.context = context;
    }

    public void setListener(DriverListener listener) {
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.description_layout_driver, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.setData(mDriversUsername.get(position), mRidersUsername.get(position));
    }

    @Override
    public int getItemCount() {
        int size = 0;
        if (mDriversUsername != null)
            size = mDriversUsername.size();
        return size;
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        public RecyclerView ridersView;
        public TextView usernameView;
        public TextView groupView;
        public String username;
        public ArrayList<String> ridersUsername;
        RiderInEventAdapter ridersDescriptionAdapter;

        public ViewHolder(View view) {
            super(view);
            itemView.setOnClickListener(this);
            ridersView = view.findViewById(R.id.bottom_sheet_riders);
            usernameView = view.findViewById(R.id.driverView);
            groupView = view.findViewById(R.id.groupView);
        }

        public void setData(String username, ArrayList<String> ridersUsername) {
            this.username = username;
            this.ridersUsername = ridersUsername;
            groupView.setText("Group "+groupCounter++);
            usernameView.setText(username);
            ridersDescriptionAdapter = new RiderInEventAdapter(ridersUsername, null);
            ridersView.setAdapter(ridersDescriptionAdapter);
            // Set the layout manager
            ridersView.setLayoutManager(new LinearLayoutManager(context));
        }

        @Override
        public void onClick(View v) {
            if (mListener != null) {
                mListener.onItemClick(username);
            }
        }
    }

    public interface DriverListener {
        void onItemClick(String username);
    }
}
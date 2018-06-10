package app.gotogether;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.maps.model.LatLng;

public class User implements Parcelable {

    private String username;
    private String startAddress;
    private LatLng startLatLng;
    private boolean isDriver;
    private int seats;

    public User(String username, String startAddress, LatLng startLatLng, boolean isDriver, int seats){
        this.username=username;
        this.startAddress=startAddress;
        this.startLatLng=startLatLng;
        this.isDriver=isDriver;
        this.seats=seats;
    }

    public User(String username, String startAddress, LatLng startLatLng){
        this.username=username;
        this.startAddress=startAddress;
        this.startLatLng=startLatLng;
        this.isDriver=false;
        this.seats=-1;
    }

    protected User(Parcel in) {
        username = in.readString();
        startAddress = in.readString();
        startLatLng = in.readParcelable(LatLng.class.getClassLoader());
        isDriver = in.readByte() != 0;
        seats = in.readInt();
    }

    public static final Creator<User> CREATOR = new Creator<User>() {
        @Override
        public User createFromParcel(Parcel in) {
            return new User(in);
        }

        @Override
        public User[] newArray(int size) {
            return new User[size];
        }
    };

    /**
     * Describe the kinds of special objects contained in this Parcelable
     * instance's marshaled representation. For example, if the object will
     * include a file descriptor in the output of {@link #writeToParcel(Parcel, int)},
     * the return value of this method must include the
     * {@link #CONTENTS_FILE_DESCRIPTOR} bit.
     *
     * @return a bitmask indicating the set of special object types marshaled
     * by this Parcelable object instance.
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Flatten this object in to a Parcel.
     *
     * @param dest  The Parcel in which the object should be written.
     * @param flags Additional flags about how the object should be written.
     *              May be 0 or {@link #PARCELABLE_WRITE_RETURN_VALUE}.
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(username);
        dest.writeString(startAddress);
        dest.writeParcelable(startLatLng, flags);
        dest.writeByte((byte) (isDriver ? 1 : 0));
        dest.writeInt(seats);
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setStartAddress(String startAddress) {
        this.startAddress = startAddress;
    }

    public void setStartLatLng(LatLng startLatLng) {
        this.startLatLng = startLatLng;
    }

    public void setDriver(boolean driver) {
        isDriver = driver;
    }

    public void setSeats(int seats) {
        this.seats = seats;
    }

    public String getUsername() {
        return username;
    }

    public String getStartAddress() {
        return startAddress;
    }

    public LatLng getStartLatLng() {
        return startLatLng;
    }

    public boolean isDriver() {
        return isDriver;
    }

    public int getSeats() {
        return seats;
    }


}

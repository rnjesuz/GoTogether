package app.gotogether;

import java.util.ArrayList;

public class UserDescription extends User{

    private String name;
    private String destination;
    private boolean isDriver = false;
    private int seats = -1;

    public UserDescription(String name, String destination, int seats){
        //super(name, destination, seats);
        this.name = name;
        this.destination = destination;
        if (seats > -1) {
            this.isDriver = true;
            this.seats = seats;
        }
    }

    public boolean isDriver() {
        return isDriver;
    }

    public int getSeats() {
        return seats;
    }

}

package app.gotogether;

public class Constraint {

    public String constraint;
    public boolean isFinalChild = false; // to know if it's the final child of the layout
    public boolean isPickUp= false;
    public boolean isDriver = false;
    public boolean isSeats = false;


    public Constraint(String contraint){
        this.constraint=contraint;
    }
}

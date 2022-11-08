package main.python.joernTestProject.src;
import java.util.*;

public class AddressBook {

    private ArrayList <BuddyInfo> Addresses;
    public AddressBook ()
    {
        Addresses = new ArrayList<>();
    }

    public void addBuddy (BuddyInfo newBuddy)
    {
       if (newBuddy != null)
       {
           Addresses.add(newBuddy);
       }
    }

    public BuddyInfo removeBuddy (int index)
    {
        if(index >= 0 && index < Addresses.size())
        {
            Addresses.remove(index);
        }
        return null;
    }

    public static void main(String[] args)
    {
        BuddyInfo bud = new BuddyInfo ("Bob", "Carleton", "613");
        AddressBook addBook = new AddressBook();
        addBook.addBuddy(bud);
        addBook.removeBuddy(0);
        System.out.println (bud.getName());
    }
}

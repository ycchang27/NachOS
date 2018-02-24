package nachos.threads;

import nachos.ag.BoatGrader;
import nachos.machine.Machine;
import java.util.ArrayList;

public class Boat
{
	// constants
	static final int child = 1;
	static final int adult = 2;
	
	static BoatGrader bg;
	
	static Lock boat_permit; // only lock holder may attempt to "row" the boat
	static boolean boat_at_Oahu = true; // boat state var
	static int boat_capacity = 0; // boat max capacity at 2*children or 1*adult
	
	static Lock Oahu_population;
	static int num_people_at_Oahu = 0; // lock holder may set this value after running

	static Lock set_finish;
	static boolean finished = false;

	static Lock allow_adult;
	static boolean adult_may_go = false;

	public static void selfTest()
	{
		BoatGrader b = new BoatGrader();

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(0, 2, b);

		//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		//  	begin(1, 2, b);

		//  	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		//  	begin(3, 3, b);
	}

	public static void begin( int adults, int children, BoatGrader b )
	{
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here
		boat_permit = new Lock();
		allow_adult = new Lock();
		set_finish = new Lock();

		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.

			/*Runnable r = new Runnable() {
				public void run() {
					SampleItinerary();
				}
			};
			KThread t = new KThread(r);
			t.setName("Sample Boat Thread");
			t.fork();*/
		
		Runnable A = new Runnable() {
			public void run() {
				AdultItinerary();
			}
		};
		Runnable C = new Runnable() {
			public void run() {
				ChildItinerary();
			}
		};
		
		boolean intStatus = Machine.interrupt().disable(); // prevent context switch until done spawning
		
		for (int i = 0; i < adults; ++i)
		{
			KThread t = new KThread(A);
			t.fork();
		}

		for (int i = 0; i < children; ++i)
		{
			KThread t = new KThread(C);
			t.fork();
		}
		
		Machine.interrupt().restore(intStatus); // spawning complete
	}

	static void AdultItinerary()
	{
		// bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
		//DO NOT PUT ANYTHING ABOVE THIS LINE. 

		/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
		   bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
		 */
	   Oahu_population.acquire();
	   num_people_at_Oahu++;
	   Oahu_population.release();
	   // sync

	}

	static void ChildItinerary()
	{
		//	 bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
		//DO NOT PUT ANYTHING ABOVE THIS LINE.
		boolean at_Oahu = true; 	// threads start at Oahu

		Oahu_population.acquire();
		num_people_at_Oahu++;		// incrment Oahu population counter when spawning in
		Oahu_population.release();
		
		while (!finished)
		{
			boat_permit.acquire(); // only one thread may access the boat at a time
			if (boat_at_Oahu && at_Oahu && boat_capacity < 2 && !finished) // if boat and thread are at Oahu
			{
				boat_capacity += child;			// add self to boat
				Oahu_population.acquire();
				num_people_at_Oahu--;			// remove self from population counter
				Oahu_population.release();
				at_Oahu = false;				// leaving Oahu
				if (num_people_at_Oahu == 0)	// Oahu is empty
				{
					finished = true;			// set finished = true
					bg.ChildRowToMolokai();		// leave Oahu
					if (boat_capacity == 2)		// if boat is full
					{
						bg.ChildRideToMolokai();	// boat includes a passenger
					}
					boat_capacity = 0;			// leave boat
					boat_at_Oahu = false;		// boat is now at Molokai
					finished = true;			// set finished = true
				}
				else if (boat_capacity == 2)	// boat is at max capacity
				{
					bg.ChildRowToMolokai();		// row self to Molokai
					bg.ChildRideToMolokai();	// passenger rides to Molokai
					boat_capacity = 0;			// leave boat
					boat_at_Oahu = false;		// boat is now at Molokai
				}
			}
			else if (!boat_at_Oahu && !at_Oahu && boat_capacity < 2 && !finished) // if boat and thread are at Molokai
			{
				bg.ChildRowToOahu();		// row self to Oahu
				at_Oahu = true;				// leaving Molokai
				Oahu_population.acquire();
				num_people_at_Oahu++;		// add self to Oahu population
				Oahu_population.release();
				boat_at_Oahu = true;		// boat is now at Oahu
				adult_may_go = true;		// flag adult to board boat

			}
			boat_permit.release();			// let next thread use boat
		}
	}

	static void SampleItinerary()
	{
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

}

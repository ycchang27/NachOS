package nachos.threads;

import nachos.ag.BoatGrader;
import nachos.machine.Machine;
import java.util.ArrayList;

public class Boat
{
	static BoatGrader bg;

	public static void selfTest()
	{
		BoatGrader b = new BoatGrader();

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(0, 2, b);
	}

	static final int adult = 2;
	static final int child = 1;

	boolean finished = false;

	Lock Oahu_pop;
	int num_adults_at_Oahu = 0;
	int num_children_at_Oahu = 0;

	Lock boat_permit;
	boolean boat_at_Oahu = true;
	int boat_capacity = 0;
	boolean allow_adult = false;

	Alarm block_sync; // block_sync.waitUntil(100);

	public static void begin( int adults, int children, BoatGrader b )
	{
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here

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

		while (!finished) KThread.yield(); // wait until forked threads are done
	}


	static void AdultItinerary()
	{
		bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
		//DO NOT PUT ANYTHING ABOVE THIS LINE. 

		boolean at_Oahu = true;

		Oahu_pop.acquire();
		num_adults_at_Oahu++;
		Oahu_pop.release();

		block_sync.waitUntil(100);
		KThread.yield();

		while (!finished)
		{
			boat_permit.acquire();
			if (boat_at_Oahu && at_Oahu && boat_capacity == 0 && allow_adult && !finished && num_children_at_Oahu < 2)
			{
				bg.AdultRowToMolokai();
				Oahu_pop.acquire();
				num_adults_at_Oahu--;
				Oahu_pop.release();
				if ((num_adults_at_Oahu + num_children_at_Oahu) == 0)
				{
					finished = true;
				}
				at_Oahu = false;
				boat_at_Oahu = false;
				allow_adult = false;
			}
			boat_permit.release();
			KThread.yield();
		}

		return;
	}

	static void ChildItinerary()
	{
		bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
		//DO NOT PUT ANYTHING ABOVE THIS LINE.
		
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

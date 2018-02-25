package nachos.threads;

import nachos.ag.BoatGrader;
import nachos.machine.Lib;
import nachos.machine.Machine;
import java.util.ArrayList;

public class Boat
{
	// constants
	static final int child = 1;
	static final int adult = 2;
	private static final char dbgBoat = 'b'; // char for Lib.debug print tool
	
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
		Lib.debug(dbgBoat, "Enter Boat.selfTest()");
		BoatGrader b = new BoatGrader();

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(0, 2, b);

		//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		//  	begin(1, 2, b);

		//  	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		//  	begin(3, 3, b);
		/* Busy waiting to prevent this test from ending too quickly */
//		for(int i = 0; i < 1000; i ++) {
//			KThread.yield();
//		}
		
		Lib.debug(dbgBoat, "Exit Boat.selfTest()");
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
		Oahu_population = new Lock();
		
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
		
		// previous implementation:
		// boolean intStatus = Machine.interrupt().disable(); // prevent context switch until done spawning
		
		// generate list of children and adults
		KThread[] adult_list = new KThread[adults];
		KThread[] child_list = new KThread[children];
		
		// implementation suggestion:
		// num_people_at_Oahu = adults + children;
		
		for (int i = 0; i < adults; ++i)
		{
			adult_list[i] = new KThread(A).setName("Adult " + (i+1));
			// previous implementation:
			// adult_list[i].fork();
		}
		for (int i = 0; i < children; ++i)
		{
			child_list[i] = new KThread(C).setName("Child " + (i+1));
			// previous implementation:
			// child_list[i].fork();
		}
		// previous implementation:
		// Machine.interrupt().restore(intStatus); // spawning complete
		
		/**
		 * Things to consider:
		 * 	- Are multiple adult/child threads necessary? How about 1 adult and 1 child?
		 *  - Do we really need locks?
		 *  - Do we need a complicated system to add num people at Oahu? Why not just set to adults + children?
		 *  - Do we need to disable interrupt when creating threads?
		 *  - Do we need to disable interrupt when calling Adult/ChildItinerary?
		 */
		
		/** 
		 * Send adults to Molokai
		 * Do the following (Prob in AdultItinerary?):
		 *  1) Send 2 children to Molokai
		 *  2) Send 1 child to Oahu (to return the boat)
		 * 	3) Send an adult to Molokai
		 *  4) Send 1 child to Oahu
		 *  5) Decrement number of adults
		*/	
		int i = 0;
		while(adults != 0) {
			// possible implementation:
			// adult_list[i].fork();
			// i++;
			break;
		}
		
		// send remaining children to Molokai
		/**
		 * Send children to Molokai
		 * Do the following (Do the following (Prob in ChildItinerary?):
		 *  1) If only a child is at Oahu, send the last child to Molokai and then
		 *  decrement the number of children by 1
		 *  2) If only 2 children are at Oahu, send the last two children to Molokai
		 *  and then decrement the number of children by 2
		 *  3) If more than 2 children are at Oahu, send 2 children to Molokai
		 *  and then send 1 child to Oahu (to return the boat) and decrement the number of 
		 *  children by 2
		 */
		i = 0;
		while(children != 0) {
			// possible implementation:
			// child_list[i].fork();
			// i++;
			break;
		}
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
		boolean at_Oahu = true;		// thread start at Oahu

		Oahu_population.acquire();
		num_people_at_Oahu++;		// increment Oahu population counter when spawning in
		Oahu_population.release();
		
		while (!finished)
		{
			boat_permit.acquire();	// only one thread may access boat at once
			if (boat_at_Oahu && at_Oahu && boat_capacity == 0 && adult_may_go && !finished) // adults must have permission before using boat
			{
				boat_capacity += adult;		// add self to boat
				Oahu_population.acquire();
				num_people_at_Oahu--;		// remove self from Oahu population
				Oahu_population.release();
				if (num_people_at_Oahu == 0)
				{
					finished = true;		// finished = true if Oahu is empty
				}
				bg.AdultRowToMolokai();		// row self to Molokai
				at_Oahu = false;			// no longer at Oahu
				boat_capacity = 0;			// leave boat
				boat_at_Oahu = false;		// boat is now at Molokai
				adult_may_go = false;		// used up permission for adults to use boat
			}
			boat_permit.release(); 		// let next thread use boat
		}
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
			if (boat_at_Oahu && at_Oahu && boat_capacity < 2 && !finished && !adult_may_go) // if boat and thread are at Oahu
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

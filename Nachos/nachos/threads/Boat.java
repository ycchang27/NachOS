package nachos.threads;

import nachos.ag.BoatGrader;
import nachos.machine.Machine;

public class Boat
{
	static BoatGrader bg;

	public static void selfTest()
	{
		BoatGrader b = new BoatGrader();

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(115, 115, b);
	}

	static final int adult = 2; // an adult takes up the boat's entire capacity
	static final int child = 1;	// a child takes up half the boat's entire capacity

	static boolean finished = false; // a thread will set this to true when program is finished

	static Lock Oahu_pop; // used to atomically access population counters
	static int num_adults_at_Oahu = 0; // number of adults at Oahu
	static int num_children_at_Oahu = 0; // number of children at Oahu

	static Lock boat_permit; // threads may only make boatgrader functions in critical sect 
	static boolean boat_at_Oahu = true; // true: boat is at Oahu; false: boat is at Molokai
	static int boat_capacity = 0; // boat is empty (max capacity = 2 children or 1 adult)
	static boolean allow_adult = false; // adults may not use boat unless a child flags them to

	static Alarm block_sync; // used to attempt block synchronization after spawning threads

	static Condition spawning;
	static Condition finishing;
	
	public static void begin( int adults, int children, BoatGrader b )
	{
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here
		Oahu_pop = new Lock();
		boat_permit = new Lock();
		block_sync = new Alarm();
		spawning = new Condition(Oahu_pop);
		finishing = new Condition(boat_permit);
		
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
//		bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
		//DO NOT PUT ANYTHING ABOVE THIS LINE. 

		boolean at_Oahu = true; // threads start at Oahu

		Oahu_pop.acquire();
		num_adults_at_Oahu++; // atomically increment the population of adults at Oahu
		spawning.sleep();
		Oahu_pop.release();

		block_sync.waitUntil(100); // block synchronization
		KThread.yield(); // let children go first

		while (!finished) // loop to finish
		{
			boat_permit.acquire(); // acquire boat lock
			// if empty boat and self are on Oahu
			// and adults have been flagged to use boat
			// and the number of children at Oahu < 2 (because children are higher priority)
			if (boat_at_Oahu && at_Oahu && boat_capacity == 0 && (allow_adult || num_children_at_Oahu == 0) && !finished && num_children_at_Oahu < 2)
			{
				bg.AdultRowToMolokai(); // go to Molokai
				Oahu_pop.acquire();
				num_adults_at_Oahu--; // atomically decrement population of adults at Oahu
				Oahu_pop.release();
				if ((num_adults_at_Oahu + num_children_at_Oahu) == 0) // if nobody left on Oahu
				{
					finished = true; // flag other threads to finish
					finishing.wakeAll();
				}
				at_Oahu = false; // no longer at Oahu
				boat_at_Oahu = false; // nor is the boat
				allow_adult = false; // used up permission
			}
			if (!finished && !at_Oahu) finishing.sleep();
			boat_permit.release(); // release boat lock
		}
		return; // finish
	}

	static void ChildItinerary()
	{
//		bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
		//DO NOT PUT ANYTHING ABOVE THIS LINE.
		
		boolean at_Oahu = true; // threads start at Oahu

		Oahu_pop.acquire();
		num_children_at_Oahu++; // atomically increment number of children at Oahu
		Oahu_pop.release();

		block_sync.waitUntil(100); // block synchronization so threads finish spawning

		while (!finished) // loop to finish
		{
			boat_permit.acquire(); // acquire boat lock
			// if self and boat are at Oahu
			// and boat is not full
			// and an adult has not been flagged to go
			if (boat_at_Oahu && at_Oahu && boat_capacity < 2 && !finished && (!allow_adult || num_children_at_Oahu > 0))
			{
				boat_capacity++; // board the boat
				Oahu_pop.acquire();
				num_children_at_Oahu--; // atomically decrement the population of children at Oahu
				Oahu_pop.release();
				at_Oahu = false; // no longer considered to be at Oahu
				if ((num_adults_at_Oahu + num_children_at_Oahu) == 0) // if Oahu population = 0
				{
					finished = true; // flag other threads to finish
					bg.ChildRowToMolokai(); // leave to Molokai
					if (boat_capacity == 2) // if boat has 2 children in it
					{
						bg.ChildRideToMolokai(); // passenger has also left to Molokai
					}
					boat_capacity = 0; // empty out boat
					boat_at_Oahu = false; // boat is now at Molokai
					finishing.wakeAll();
				}
				else if (boat_capacity == 2) // if boat is full
				{
					bg.ChildRowToMolokai(); // both children 
					bg.ChildRideToMolokai(); // go to Molokai
					boat_capacity = 0; // empty out boat
					boat_at_Oahu = false; // boat is now at Molokai
				}
				else if (num_children_at_Oahu == 0) // if no children left at Oahu
				{
					// stay on Oahu (because 1 child must be the last to leave Oahu)
					boat_capacity--; // empty out boat
					Oahu_pop.acquire();
					num_children_at_Oahu++; // atomically increment population of children at Oahu
					Oahu_pop.release();
					at_Oahu = true; // staying at Oahu still
				}
			}
			// if self and boat are at Molokai and boat is not filled
			else if (!boat_at_Oahu && !at_Oahu && boat_capacity < 2 && !finished)
			{
				bg.ChildRowToOahu(); // go back to Oahu
				at_Oahu = true; // now at Oahu
				Oahu_pop.acquire();
				spawning.wakeAll();
				num_children_at_Oahu++; // atomically increment number of children at Oahu
				Oahu_pop.release();
				boat_at_Oahu = true; // boat is now at Oahu
				allow_adult = true; // flag that an adult may go to Molokai
			}
			boat_permit.release(); // release boat lock
			if (!finished) KThread.yield(); // yield to another thread
		}

		return; // finish
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

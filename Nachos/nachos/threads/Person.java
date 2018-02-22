package nachos.threads;

public class Person
{
	int value; // 1 for child, 2 for adult
	int num_people_at_Oahu; // thread's memory of people left at Oahu
	boolean at_Oahu = true; // thread's state var
	
	KThread thread; // execution thread associated with this object
	
	public Person(Runnable r, int value) // e.g. new Person(new Runnable(...), child)
	{
		this.value = value;
		thread = new KThread(r);
		thread.fork(); // allow thread to begin execution when possible
	}
}

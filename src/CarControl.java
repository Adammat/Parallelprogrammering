//Prototype implementation of Car Control
//Mandatory assignment
//Course 02158 Concurrent Programming, DTU, Fall 2018

//Hans Henrik Lovengreen      Oct 8, 2018


import java.awt.Color;

class Gate {

    Semaphore g = new Semaphore(0);
    Semaphore e = new Semaphore(1);
    boolean isopen = false;

    public void pass() throws InterruptedException {
        g.P(); 
        g.V();
    }

    public void open() {
        try { e.P(); } catch (InterruptedException e) {}
        if (!isopen) { g.V();  isopen = true; }
        e.V();
    }

    public void close() {
        try { e.P(); } catch (InterruptedException e) {}
        if (isopen) { 
            try { g.P(); } catch (InterruptedException e) {}
            isopen = false;
        }
        e.V();
    }

}

class Conductor extends Thread {

    final static int steps = 10;
    static final int upperBarRow = 4;
    static final int lowerBarRow = 5;

    double basespeed = 6.0;          // Tiles per second
    double variation =  50;          // Percentage of base speed

    CarDisplayI cd;                  // GUI part
    Semaphore[][] tiles;

    int no;                          // Car number
    Pos startpos;                    // Start position (provided by GUI)
    Pos barpos;                      // Barrier position (provided by GUI)
    Color col;                       // Car  color
    Gate mygate;                     // Gate at start position

    Pos curpos;                      // Current position 
    Pos newpos;                      // New position to go to

    Semaphore curTile;				//Current tile semaphore
    Semaphore newTile;				//New tile semaphore
    
    Barrier bar;
    
    
    public Conductor(int no, CarDisplayI cd, Gate g, Semaphore[][] tiles, Barrier bar) {

    	this.tiles = tiles;
        this.no = no;
        this.cd = cd;
        this.bar = bar;
        mygate = g;
        startpos = cd.getStartPos(no);
        barpos   = cd.getBarrierPos(no);  // For later use
        
        
        col = chooseColor();

        // special settings for car no. 0
        if (no==0) {
            basespeed = -1.0;  
            variation = 0; 
        }
    }

    public synchronized void setSpeed(double speed) { 
        basespeed = speed;
    }

    public synchronized void setVariation(int var) { 
        if (no != 0 && 0 <= var && var <= 100) {
            variation = var;
        }
        else
            cd.println("Illegal variation settings");
    }

    synchronized double chooseSpeed() { 
        double factor = (1.0D+(Math.random()-0.5D)*2*variation/100);
        return factor*basespeed;
    }

    Color chooseColor() { 
        return Color.blue; // You can get any color, as longs as it's blue 
    }

    Pos nextPos(Pos pos) {
        // Get my track from display
        return cd.nextPos(no,pos);
    }

    boolean atGate(Pos pos) {
        return pos.equals(startpos);
    }

	private boolean atBarrier(Pos pos) {
		//Checks if current car is at the barrier
		return pos.equals(cd.getBarrierPos(no));
	}
    
    public void run() {
        try {
            CarI car = cd.newCar(no, col, startpos);
            curpos = startpos;
            curTile = tiles[curpos.row][curpos.col];
            cd.register(car);

            
            while (true) { 
            	
                if (atGate(curpos)) { 
                    mygate.pass(); 
                    car.setSpeed(chooseSpeed());
                } else if (atBarrier(curpos)) {
                	bar.sync();
                }
                
                
                	
                
                
                newpos = nextPos(curpos);
                newTile = tiles[newpos.row][newpos.col];
                
                if(newTile != curTile)
                	//Waits for new position to free up
                	tiles[newpos.row][newpos.col].P();
                
                
                car.driveTo(newpos);
                //Frees up old position
                if(newTile != curTile)
                	tiles[curpos.row][curpos.col].V();
                

                curpos = newpos;
                curTile = newTile;
            }

        } catch (Exception e) {
            cd.println("Exception in Car no. " + no);
            System.err.println("Exception in Car no. " + no + ":" + e);
            e.printStackTrace();
        }
    }



}

public class CarControl implements CarControlI{

	Barrier bar;
    CarDisplayI cd;           // Reference to GUI
    Conductor[] conductor;    // Car controllers
    Gate[] gate;              // Gates
    Semaphore[][] tiles;
    static final int ROWS = 11;
    static final int COLS = 12;

    
    Semaphore tempSem;
    
    public CarControl(CarDisplayI cd) {
        this.cd = cd;
        conductor = new Conductor[9];
        gate = new Gate[9];
        tiles = new Semaphore[ROWS][COLS];
        bar = new Barrier();
        
        //Setup tiles
        for(int i = 0; i < ROWS; i++){
        	for (int j = 0; j<COLS; j++){
        		tiles[i][j] = new Semaphore(1);
        	}
        }
        
        //Setup Alley
        tempSem = new Semaphore(1);
        for (int i = 1; i < ROWS-1; i++) { tiles[i][0] = tempSem; }
        //Adds the area around the shed to the alley
        tiles[ROWS-2][1] = tempSem;
        tiles[ROWS-2][2] = tempSem;
        
        for (int no = 0; no < 9; no++) {
            gate[no] = new Gate();
            conductor[no] = new Conductor(no,cd,gate[no],tiles,bar);
            conductor[no].setName("Conductor-" + no);
            conductor[no].start();
        } 
    }

    public void startCar(int no) {
        gate[no].open();
    }

    public void stopCar(int no) {
        gate[no].close();
    }

    public void barrierOn() { 
        bar.on();
    	
    }

    public void barrierOff() {
    	bar.off();
    }

    public void barrierSet(int k) { 
        cd.println("Barrier threshold setting not implemented in this version");
        // This sleep is solely for illustrating how blocking affects the GUI
        // Remove when feature is properly implemented.
        try { Thread.sleep(3000); } catch (InterruptedException e) { }
    }

    public void removeCar(int no) { 
        cd.println("Remove Car not implemented in this version");
    }

    public void restoreCar(int no) { 
        cd.println("Restore Car not implemented in this version");
    }

    /* Speed settings for testing purposes */

    public void setSpeed(int no, double speed) { 
        conductor[no].setSpeed(speed);
    }

    public void setVariation(int no, int var) { 
        conductor[no].setVariation(var);
    }

}

class Barrier {
	
	Boolean flag = false;
	Semaphore sem = new Semaphore(0);
	int counter = 0;
	final int numberOfCars = 9;
	
	public void sync() throws InterruptedException {
		if(flag) {
			counter++;
			if (counter == numberOfCars) {
				//Frees the other cars
				for (int i = 1; i <counter; i++){
					sem.V();
				}
				//Reset counter
				counter = 0;
			} else {
				sem.P();		
			}
		}
	}
	
	
	public void on(){
		flag = true;
		
		
	}
	
	public void off(){
		flag = false;
		//Frees the trapped cars
		for (int i = 0; i <counter; i++){
			sem.V();
		}
		counter = 0;
	}
	
	
}






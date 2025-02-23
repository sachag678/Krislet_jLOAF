//
//	File:			Brain.java
//	Author:		Krzysztof Langner
//	Date:			1997/04/28
//
//    Modified by:	Paul Marlow

//    Modified by:      Edgar Acosta
//    Date:             March 4, 2008

//	  Modified by: 		Sacha Gunaratne
// 	  Date:				April 16 2017

import java.lang.Math;
import java.util.Vector;
import java.util.regex.*;

import org.jLOAF.action.Action;
import org.jLOAF.action.AtomicAction;
import org.jLOAF.casebase.Case;
import org.jLOAF.casebase.CaseBase;
import org.jLOAF.inputs.AtomicInput;
import org.jLOAF.inputs.ComplexInput;
import org.jLOAF.inputs.Feature;
import org.jLOAF.inputs.Input;
import org.jLOAF.inputs.StateBasedInput;
import org.jLOAF.preprocessing.filter.CaseBaseFilter;
import org.jLOAF.preprocessing.filter.casebasefilter.NoFilter;
import org.jLOAF.preprocessing.filter.casebasefilter.Sampling;
import org.jLOAF.preprocessing.filter.casebasefilter.UnderSampling;
import org.jLOAF.reasoning.BayesianReasoner;
import org.jLOAF.reasoning.TBReasoning;
import org.jLOAF.reasoning.WeightedKNN;
import org.jLOAF.sim.AtomicSimilarityMetricStrategy;
import org.jLOAF.sim.ComplexSimilarityMetricStrategy;
import org.jLOAF.sim.SimilarityMetricStrategy;
import org.jLOAF.sim.StateBasedSimilarity;
import org.jLOAF.sim.StateBased.KOrderedSimilarity;
import org.jLOAF.sim.atomic.EuclideanDistance;
import org.jLOAF.sim.complex.GreedyMunkrezMatching;
import org.jLOAF.sim.complex.Mean;
import org.jLOAF.sim.complex.WeightedMean;
import org.jLOAF.weights.SimilarityWeights;

import AgentModules.RoboCupAction;
import AgentModules.RoboCupAgent;
import AgentModules.RoboCupInput;

class Brain extends Thread implements SensorInput
{
    //---------------------------------------------------------------------------
    // This constructor:
    // - stores connection to krislet
    // - starts thread for this object
    public Brain(SendCommand krislet, 
		 String team, 
		 char side, 
		 int number, 
		 String playMode,
		 String matchType, 
		 String cbname)
    {
	m_timeOver = false;
	m_krislet = krislet;
	m_memory = new Memory();
	//m_team = team;
	m_side = side;
	// m_number = number;
	m_playMode = playMode;
	m_latest = null;
	
	
	//load casebase
	System.out.println("Loading CaseBase...");
	CaseBase cb = CaseBase.load(cbname);
	System.out.println("Finished Loading CaseBase...");
	
	//filter
	System.out.println(cb.getSize());
	//CaseBaseFilter s = new NoFilter(null);
	//CaseBaseFilter s = new UnderSampling(null);
	//CaseBaseFilter s = new Sampling(null);
	//CaseBase processed_cb = s.filter(cb);
	//System.out.println(processed_cb.getSize());
	//create agent
	System.out.println("Creating Agent...");
	agent = new RoboCupAgent();
	
	//set reasoning
	System.out.println("Setting Reasoner...");
	//agent.setR(new WeightedKNN(5,cb));
	//agent.setR(new TBReasoning(cb));
	agent.setR(new BayesianReasoner(cb, "bayesian.txt"));
	
	start();
    }

    public void run()
    {
    ObjectInfo object;
	// first put it somewhere on my side
	if(Pattern.matches("^before_kick_off.*",m_playMode))
	    m_krislet.move( -Math.random()*52.5 , 34 - Math.random()*68.0 );

	while( !m_timeOver )
	    {	
		//get input and action - make decision
		Input input = this.Convert2Complex(m_memory, m_latest);
		//Input input = ((RoboCupPerception)agent.getP()).sense(m_memory);
		if(input!=null){
//			Action action = agent.getR().selectAction(input);
//			agent.run(input);

			//cast to RoboCupAction
			RoboCupAction a = agent.run(input);
			
			//cases
			if(a.getName().equals("turn")){
				System.out.println("turn");
				//get the angle of the feature
				
				//m_krislet.turn(((AtomicAction)a.get("turnAngle")).getFeature().getValue());
				object = m_memory.getObject("ball");
				if( object == null )
			    {
				// If you don't know where is ball then find it
				m_krislet.turn(40);
				m_memory.waitForNewInfo();
			    }
				else if( object.m_distance > 1.0 )
			    {
				// If ball is too far then
				// turn to ball or 
				// if we have correct direction then go to ball
				m_krislet.turn(object.m_direction);
				
				//System.out.println("turn "+ ((AtomicAction)a.get("turnAngle")).getFeature().getValue());
			    }else if( object.m_distance <= 1.0){
			    	m_krislet.turn(40);
			    }
			}
			else if(a.getName().equals("dash")){
				System.out.println("dash");
				//get the power of the dash
				//System.out.println("dash "+ ((AtomicAction)a.get("dashPower")).getFeature().getValue());
				//m_krislet.dash(((AtomicAction)a.get("dashPower")).getFeature().getValue());
				object = m_memory.getObject("ball");
				if( object == null )
			    {
					m_krislet.dash(100);
				m_memory.waitForNewInfo();
			    }else{
				
				m_krislet.dash(10*object.m_distance);
			    }
				
			}
			else if(a.getName().equals("kick")){
				System.out.println("kick");
				//System.out.println("kick "+ ((AtomicAction)a.get("kickPower")).getFeature().getValue() + " " +  ((AtomicAction)a.get("kickAngle")).getFeature().getValue());
				//m_krislet.kick(((AtomicAction)a.get("kickPower")).getFeature().getValue(), ((AtomicAction)a.get("kickAngle")).getFeature().getValue());
				if( m_side == 'l' ){
				    object = m_memory.getObject("goal r");
				}
				else{
				    object = m_memory.getObject("goal l");
				}
				
				if( object == null )
			    {
				System.out.println("Can't see goal");
				m_krislet.kick(100,0);
				m_memory.waitForNewInfo();
			    }
			else
				m_krislet.kick(100, object.m_direction);
			}
			
			m_latest = new Case(input, a);

		}else{
			m_memory.waitForNewInfo();
		}
		// sleep one step to ensure that we will not send
				// two commands in one cycle.
		try{
		    Thread.sleep(2*SoccerParams.simulator_step);
		}catch(Exception e){}
		
	    }
	    
	
	m_krislet.bye();
    }


    //===========================================================================
    // Here are suporting functions for implement logic


    //===========================================================================
    // Implementation of SensorInput Interface

    //---------------------------------------------------------------------------
    // This function sends see information
    public void see(VisualInfo info)
    {
	m_memory.store(info);
    }


    //---------------------------------------------------------------------------
    // This function receives hear information from player
    public void hear(int time, int direction, String message)
    {
    }

    //---------------------------------------------------------------------------
    // This function receives hear information from referee
    public void hear(int time, String message)
    {						 
	if(message.compareTo("time_over") == 0)
	    m_timeOver = true;

    }
    //----------------------------------------------------------------------------
    //converts a memory into a complex input
    private Input Convert2Complex(Memory m, Case latest) {
		//
    	boolean want_flags = false;
    	//get visualinfo
		VisualInfo info = m.getVisualInfo();
		//get objectInfo vector
		
		//similarityMetrics
		//atomic
		AtomicSimilarityMetricStrategy Atomic_strat = new EuclideanDistance();
		//complex
		ComplexSimilarityMetricStrategy ballGoal_strat = new Mean();
		ComplexSimilarityMetricStrategy flag_strat = new GreedyMunkrezMatching();

		//weights
		SimilarityWeights sim_weights = new SimilarityWeights();
		sim_weights.setFeatureWeight("ball", 1);
		sim_weights.setFeatureWeight("goal r", 1);
		sim_weights.setFeatureWeight("goal l", 1); 

		ComplexSimilarityMetricStrategy RoboCup_strat = new WeightedMean(sim_weights);
		StateBasedSimilarity stateBasedsim = new KOrderedSimilarity(1);
				
		if(info!=null){
			Vector<ObjectInfo> m_objects = info.m_objects;
			StateBasedInput statebasedInput = new StateBasedInput("StateBasedSim",stateBasedsim);
			RoboCupInput input = new RoboCupInput("SenseEnvironment", RoboCup_strat);
			
			for(ObjectInfo obj: m_objects){
				//add ball info
				
				if(obj.m_type.equals("ball")){
					ComplexInput ball = new ComplexInput("ball", ballGoal_strat);
					Feature dist = new Feature(obj.m_distance);
					Feature dir = new Feature(obj.m_direction);
					AtomicInput b_dist = new AtomicInput("ball_dist",dist, Atomic_strat);
					AtomicInput b_dir = new AtomicInput("ball_dir",dir, Atomic_strat);
					ball.add(b_dist);
					ball.add(b_dir);
					input.add(ball);
				}
				
				//add goal r info
				
				if(obj.m_type.equals("goal r")){
					ComplexInput goal_r = new ComplexInput("goal r", ballGoal_strat);
					Feature dist = new Feature(obj.m_distance);
					Feature dir = new Feature(obj.m_direction);
					AtomicInput g_dist = new AtomicInput("goal_distR",dist, Atomic_strat);
					AtomicInput g_dir = new AtomicInput("goal_dirR",dir, Atomic_strat);
					goal_r.add(g_dist);
					goal_r.add(g_dir);
					input.add(goal_r);
				}
				
				
				//add goal l info
				
				if(obj.m_type.equals("goal l")){
					ComplexInput goal_l = new ComplexInput("goal l", ballGoal_strat);
					Feature dist = new Feature(obj.m_distance);
					Feature dir = new Feature(obj.m_direction);
					AtomicInput g_dist = new AtomicInput("goal_distL",dist, Atomic_strat);
					AtomicInput g_dir = new AtomicInput("goal_dirL",dir, Atomic_strat);
					goal_l.add(g_dist);
					goal_l.add(g_dir);
					input.add(goal_l);
				}
				
				
				if (want_flags){
					ComplexInput allflags = new ComplexInput("flags", flag_strat); 
					String [] flags = {"flag c b", "flag l b","flag r b", "flag c t","flag l t", "flag r t", "flag c", "flag p l t", "flag p l c", "flag p l b", "flag p r t", "flag p r c", "flag p r b" };
					String [] flag_names = {"fcb", "flb","frb", "fct","flt", "frt", "fc","fplt", "fplc", "fplb", "fprt", "fprc", "fprb"}; 
					
					for(int i =0;i<flags.length;i++){
						if(obj.m_type.equals(flags[i]) && ((FlagInfo) obj).m_num==0){
							Feature dist = new Feature(obj.m_distance);
							Feature dir = new Feature(obj.m_direction);
							AtomicInput fdist = new AtomicInput(flag_names[i]+"_dist",dist, Atomic_strat);
							AtomicInput fdir = new AtomicInput(flag_names[i]+"_dir",dir, Atomic_strat);
							ComplexInput f = new ComplexInput(flag_names[i], ballGoal_strat);
							f.add(fdist);
							f.add(fdir);
							allflags.add(f);
						}
					}
					
					String [] flags_top_bot = {"flag t l", "flag t r","flag b l", "flag b r"};
					String [] flag_names_top_bot = {"ftl","ftr","fbl","fbr"};
					int [] flag_dist_top_bot = {50, 40,30,20,10};
					
					for(int i =0;i<flags_top_bot.length;i++){
						for(int j =0; j<flag_dist_top_bot.length;j++){
							if(obj.m_type.equals(flags_top_bot[i]) && ((FlagInfo) obj).m_num==flag_dist_top_bot[j]){
								Feature dist = new Feature(obj.m_distance);
								Feature dir = new Feature(obj.m_direction);
								AtomicInput fdist = new AtomicInput(flag_names_top_bot[i]+String.valueOf(flag_dist_top_bot[j])+"_dist",dist, Atomic_strat);
								AtomicInput fdir = new AtomicInput(flag_names_top_bot[i]+String.valueOf(flag_dist_top_bot[j])+"_dir",dir, Atomic_strat);
								ComplexInput f = new ComplexInput(flag_names_top_bot[i]+String.valueOf(flag_dist_top_bot[j]), ballGoal_strat);
								f.add(fdist);
								f.add(fdir);
								allflags.add(f);
							}
						}
					}
					
					String [] flags_r_l = {"flag r t", "flag r b","flag l t", "flag l b"};
					String [] flag_names_r_l = {"frt","frb","flt","flb"};
					int [] flag_dist_r_l = {30,20,10};
					
					for(int i =0;i<flags_r_l.length;i++){
						for(int j =0; j<flag_dist_r_l.length;j++){
							if(obj.m_type.equals(flags_r_l[i]) && ((FlagInfo) obj).m_num==flag_dist_r_l[j]){
								Feature dist = new Feature(obj.m_distance);
								Feature dir = new Feature(obj.m_direction);
								AtomicInput fdist = new AtomicInput(flag_names_r_l[i]+String.valueOf(flag_dist_r_l[j])+"_dist",dist, Atomic_strat);
								AtomicInput fdir = new AtomicInput(flag_names_r_l[i]+String.valueOf(flag_dist_r_l[j])+"_dir",dir, Atomic_strat);
								ComplexInput f = new ComplexInput(flag_names_r_l[i]+String.valueOf(flag_dist_r_l[j]), ballGoal_strat);
								f.add(fdist);
								f.add(fdir);
								allflags.add(f);
							}
						}
					}
					input.add(allflags);
				}
				
			}
			statebasedInput.setInput(input);
			statebasedInput.setCase(latest);
			return statebasedInput;
		}else{
			System.out.println("Error :There is no visual information");
			return null;
		}
		
	}

    //===========================================================================
    
    // Private members
    private SendCommand	                m_krislet;			// robot which is controled by this brain
    private Memory			m_memory;				// place where all information is stored
    private char			m_side;
    volatile private boolean		m_timeOver;
    private String                      m_playMode;
    private RoboCupAgent agent; //robocup agent
    private Case m_latest;
    
}

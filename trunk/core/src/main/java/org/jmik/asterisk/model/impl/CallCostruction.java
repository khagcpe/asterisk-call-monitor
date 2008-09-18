package org.jmik.asterisk.model.impl;

import java.util.Stack;

import org.apache.log4j.Logger;
import org.asteriskjava.manager.event.HangupEvent;
import org.asteriskjava.manager.event.ManagerEvent;
import org.asteriskjava.manager.event.MeetMeJoinEvent;
import org.asteriskjava.manager.event.NewChannelEvent;
import org.asteriskjava.manager.event.NewExtenEvent;
import org.asteriskjava.manager.event.NewStateEvent;
import org.jmik.asterisk.model.Provider;
import org.jmik.asterisk.model.utils.DateFormatter;

/**
 * FSM that keeps track of the events.
 * instantiate Call
 * 
 * @author Michele La Porta
 */
public class CallCostruction {

	private static Logger logger = Logger.getLogger(CallCostruction.class);

	public static final int WAIT_NEW_EXT = 1;
	public static final int WAIT_NEW_STATE_UP = 3;
	public static final int WAIT_MEETMEJOIN = 4;

	private int waitState;
	private Provider provider;
	public Stack<ManagerEvent> eventStack = new Stack<ManagerEvent>();
	
	public CallCostruction(Provider provider,NewChannelEvent newChannelEvent){
		if(provider == null){
			logger.error("provider " + provider);
			throw new IllegalArgumentException("provider can not be null");
		}
		if(newChannelEvent == null){
			logger.error("newChannelEvent " + newChannelEvent);
			throw new IllegalArgumentException("newChannelEvent can not be null");
		}
		if("Down".equals(newChannelEvent.getState()) == false){
			logger.error("newChannelEvent Ring? " + newChannelEvent.getState());
			throw new IllegalArgumentException("the state of the nce must be Ring");
		}
		
		this.provider = provider;
		waitState = WAIT_NEW_EXT;
		eventStack.push(newChannelEvent);
		if(logger.isDebugEnabled())
			logger.debug(this + " " + newChannelEvent);
		
	}

	public boolean processEvent(ManagerEvent event){
		
		if(logger.isDebugEnabled())
			logger.debug("processEvent " + event.getClass().getSimpleName()+" " + waitState);
		
		switch (waitState) 
		{
		
			case WAIT_NEW_EXT:
				
				if(event instanceof NewExtenEvent){

					NewExtenEvent newExtenEvent = (NewExtenEvent)event;
					NewChannelEvent newChannelEvent = (NewChannelEvent)eventStack.peek();
					
					if(newExtenEvent.getUniqueId().equals(newChannelEvent.getUniqueId())){
						
						if(newExtenEvent.getApplication().equals("Dial")) {						
							newChannelEvent = (NewChannelEvent) eventStack.pop();						
							
							String callId = newExtenEvent.getContext() + "|" + newChannelEvent.getCallerId() + "|" 
								+ newExtenEvent.getExtension() + "|" + DateFormatter.format(newChannelEvent.getDateReceived());
							
							Channel.Descriptor dialingChannelDesc = new Channel.Descriptor(newChannelEvent.getChannel(), 
									newChannelEvent.getDateReceived(), new CallEndpoint(newChannelEvent.getCallerId()));
							
							Channel.Descriptor dialedChannelDesc = new Channel.Descriptor(
								new CallEndpoint(newExtenEvent.getExtension())); 
							
							TwoPartiesCall twoPartiesCall = new TwoPartiesCall(callId, newChannelEvent.getDateReceived(), 
								dialingChannelDesc, dialedChannelDesc);
												
							provider.removeCallConstruction(this);
							provider.attachCall(twoPartiesCall);
							if(logger.isDebugEnabled())
								logger.debug("attachCall " +twoPartiesCall);
							return true;
						} else {
							eventStack.push(newExtenEvent);
							waitState = WAIT_NEW_STATE_UP;
							if(logger.isDebugEnabled())
								logger.debug("WAIT_NEW_STATE_UP");
							return true;						
						}
					}
					
				}else if(event instanceof HangupEvent) {
					HangupEvent hangupEvent = (HangupEvent) event;
					NewChannelEvent newChannelEvent = (NewChannelEvent) eventStack.peek();
					
					if(hangupEvent.getUniqueId().equals(newChannelEvent.getUniqueId())) {
						provider.removeCallConstruction(this);
						if(logger.isDebugEnabled())
							logger.debug("HangupEvent removeCallConstruction " +this);
					}
				}
				break;
				
			case WAIT_NEW_STATE_UP:
				
				if(event instanceof NewStateEvent){
					
					NewStateEvent newStateEvent = (NewStateEvent)event;
					NewExtenEvent newExtenEvent = (NewExtenEvent)eventStack.peek();
					
					if(newStateEvent.getUniqueId().equals(newExtenEvent.getUniqueId())){
						
						if(newExtenEvent.getApplication().equals("MeetMe") || newExtenEvent.getApplication().equals("AGI")){
							waitState = WAIT_MEETMEJOIN;
							eventStack.push(newStateEvent);
							
						}
						else/*(newExtenEvent.getApplication().equals("Playback") )*/{

							newExtenEvent = (NewExtenEvent)eventStack.pop();
							NewChannelEvent newChannelEvent = (NewChannelEvent)eventStack.pop();

							String callId = newExtenEvent.getContext()
								+ "|"
								+ newChannelEvent.getCallerId()
								+ "|"
								+ newExtenEvent.getExtension()
								+ "|"
								+ DateFormatter.format(newChannelEvent
										.getDateReceived());

							Channel.Descriptor channelDescriptor = new Channel.Descriptor(newChannelEvent.getChannel(), newChannelEvent.getDateReceived(), new CallEndpoint(newChannelEvent.getCallerId()));
							
							//instantiate a new single party call 
							SinglePartyCall singleCall = new SinglePartyCall(callId,newChannelEvent.getDateReceived(),CallState.ACTIVE.state(),channelDescriptor);
							provider.removeCallConstruction(this);
							provider.attachCall(singleCall);
							logger.info("instantiate a new singleCall " + singleCall);
							
						}
					}
				}else if(event instanceof HangupEvent) {
					HangupEvent he = (HangupEvent) event;
					NewExtenEvent newExtenEvent = (NewExtenEvent) eventStack.peek();
					if(he.getUniqueId().equals(newExtenEvent.getUniqueId())) {
						provider.removeCallConstruction(this);
						logger.info("HangupEvent removeCallConstruction " + this);
						
					}
				}
				
				break;
				
			case WAIT_MEETMEJOIN:
				if(event instanceof MeetMeJoinEvent) {
					MeetMeJoinEvent mmje = (MeetMeJoinEvent) event;
					NewStateEvent nse = (NewStateEvent) eventStack.pop();
					NewExtenEvent nee = (NewExtenEvent) eventStack.pop();
					NewChannelEvent nce = (NewChannelEvent) eventStack.pop();				
					String roomId = mmje.getMeetMe();
					if(logger.isDebugEnabled())
						logger.debug("Processing MeetMeJoinEvent " + mmje.getMeetMe());
					
					boolean confCallExist = false;
					
					for(Call attachedCall : provider.getAttachedCalls()) {
						if(attachedCall instanceof ConferenceCall) {								
							ConferenceCall confCall = (ConferenceCall) attachedCall;						
							
							if(confCall.getRoomId().equals(roomId)) {
								confCallExist = true;							
								Channel.Descriptor newChannelDescritor = new Channel.Descriptor(nce.getChannel(), 
									nce.getDateReceived(), new CallEndpoint(nee.getExtension()));
								confCall.addChannel(newChannelDescritor);							
								provider.removeCallConstruction(this);
								if(logger.isDebugEnabled())
									logger.debug("confCallExist removeCallConstruction " + this);
								return true;
							}
						}
					}	
					
					if(!confCallExist) {
						
						Channel.Descriptor channelDesc = new Channel.Descriptor(nce.getChannel(), 
							nce.getDateReceived(), new CallEndpoint(nee.getExtension()));
						
						ConferenceCall conferenceCall = new ConferenceCall(roomId, mmje.getDateReceived(), channelDesc);
						provider.removeCallConstruction(this);
						provider.attachCall(conferenceCall);
						if(logger.isDebugEnabled())
							logger.debug("attach " + conferenceCall);
						return true;					
					} 
				}				
				break;
		}
		
		return false;
	}
	
	public int getWaitState() {
		return waitState;
	}

	public Stack<ManagerEvent> getEventStack() {
		return eventStack;
	}
	
}

package custom.loadbalancer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionNetworkLayerSource;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;

/**
 * Module to perform round-robin load balancing.
 * 
 */
public class LoadBalancer implements IOFMessageListener, IFloodlightModule {

	// Interface to Floodlight core for interacting with connected switches
	protected IFloodlightProviderService floodlightProvider;
	
	// Interface to the logging system
	protected static Logger logger;
	
	// IP and MAC address for our logical load balancer
	private final static int LOAD_BALANCER_IP = IPv4.toIPv4Address("192.168.1.254");
	private final static byte[] LOAD_BALANCER_MAC = Ethernet.toMACAddress("00:00:00:00:00:10");
	
	// Rule timeouts
	private final static short IDLE_TIMEOUT = 60; // in seconds
	private final static short HARD_TIMEOUT = 0; // infinite
	
	private static class Server
	{
		private int ip;
		private byte[] mac;
		private short port;
		
		public Server(String ip, String mac, short port) {
			this.ip = IPv4.toIPv4Address(ip);
			this.mac = Ethernet.toMACAddress(mac);
			this.port = port;
		}
		
		public int getIP() {
			return this.ip;
		}
		
		public byte[] getMAC() {
			return this.mac;
		}
		
		public short getPort() {
			return this.port;
		}
	}
	
	final static Server[] SERVERS = {
		new Server("192.168.1.11", "00:00:00:00:00:11", (short)1),
		new Server("192.168.1.12", "00:00:00:00:00:12", (short)2)
	};
	private int lastServer = 0;
	
	/**
	 * Provides an identifier for our OFMessage listener.
	 * Important to override!
	 * */
	@Override
	public String getName() {
		return LoadBalancer.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// Auto-generated method stub
		return null;
	}

	/**
	 * Tells the module loading system which modules we depend on.
	 * Important to override! 
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService >> floodlightService = 
			new ArrayList<Class<? extends IFloodlightService>>();
		floodlightService.add(IFloodlightProviderService.class);
		return floodlightService;
	}

	/**
	 * Loads dependencies and initializes data structures.
	 * Important to override! 
	 */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		logger = LoggerFactory.getLogger(LoadBalancer.class);
	}

	/**
	 * Tells the Floodlight core we are interested in PACKET_IN messages.
	 * Important to override! 
	 * */
	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}
	
	/**
	 * Receives an OpenFlow message from the Floodlight core and initiates the appropriate control logic.
	 * Important to override!
	 */
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		
		// We only care about packet-in messages
		if (msg.getType() != OFType.PACKET_IN) { 
			// Allow the next module to also process this OpenFlow message
		    return Command.CONTINUE;
		}
		OFPacketIn pi = (OFPacketIn)msg;
				
		// Parse the received packet		
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort());
        
		// We only care about TCP packets
		if (match.getDataLayerType() != Ethernet.TYPE_IPv4 && match.getDataLayerType() != Ethernet.TYPE_ARP) {
			// Allow the next module to also process this OpenFlow message
		    return Command.CONTINUE;
		}
		
		// We only care about packets which are sent to the logical load balancer
		if (match.getNetworkDestination() != LOAD_BALANCER_IP) {
			// Allow the next module to also process this OpenFlow message
		    return Command.CONTINUE;
		}
		
		if (match.getDataLayerType() == Ethernet.TYPE_ARP) {
			
			// Receive an ARP request
			logger.info("Received an ARP request for the load balancer");
        	handleARPRequest(sw, pi, cntx);
        	
        } else {
			
			logger.info("Received an IPv4 packet destined for the load balancer");
			loadBalanceFlow(sw, pi, cntx);
        }
       
		// Do not continue processing this OpenFlow message
		return Command.STOP;
    }
	
	/**
	 * Sends a packet out to the switch
	 */
	private void pushPacket(IOFSwitch sw, OFPacketIn pi, 
			ArrayList<OFAction> actions, short actionsLength) {
		
		// create an OFPacketOut for the pushed packet
        OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory()
                		.getMessage(OFType.PACKET_OUT);        
        
        // Update the inputPort and bufferID
        po.setInPort(pi.getInPort());
        po.setBufferId(pi.getBufferId());
                
        // Set the actions to apply for this packet		
		po.setActions(actions);
		po.setActionsLength(actionsLength);
	        
        // Set data if it is included in the packet in but buffer id is NONE
        if (pi.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
            byte[] packetData = pi.getPacketData();
            po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
                    + po.getActionsLength() + packetData.length));
            po.setPacketData(packetData);
        } else {
            po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
                    + po.getActionsLength()));
        }        
        
        // Push the packet to the switch
        try {
            sw.write(po, null);
        } catch (IOException e) {
            logger.error("failed to write packetOut: ", e);
        }
	}

	/**
	 * Handle ARP Request and reply it with load balancer's MAC address
	 */
	private void handleARPRequest(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		
		logger.debug("Handle ARP request");
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		if (! (eth.getPayload() instanceof ARP))
			return;
		ARP arpRequest = (ARP) eth.getPayload();
		
		// generate ARP reply
		IPacket arpReply = new Ethernet()
			.setSourceMACAddress(LoadBalancer.LOAD_BALANCER_MAC)
			.setDestinationMACAddress(eth.getSourceMACAddress())
			.setEtherType(Ethernet.TYPE_ARP)
			.setPriorityCode(eth.getPriorityCode())
			.setPayload(
				new ARP()
				.setHardwareType(ARP.HW_TYPE_ETHERNET)
				.setProtocolType(ARP.PROTO_TYPE_IP)
				.setHardwareAddressLength((byte) 6)
				.setProtocolAddressLength((byte) 4)
				.setOpCode(ARP.OP_REPLY)
				.setSenderHardwareAddress(LoadBalancer.LOAD_BALANCER_MAC)
				.setSenderProtocolAddress(LoadBalancer.LOAD_BALANCER_IP)
				.setTargetHardwareAddress(arpRequest.getSenderHardwareAddress())
				.setTargetProtocolAddress(arpRequest.getSenderProtocolAddress()));
		
		sendARPReply(arpReply, sw, OFPort.OFPP_NONE.getValue(), pi.getInPort());
	}
	
	/**
	 * Sends ARP reply out to the switch
	 */
	private void sendARPReply(IPacket packet, IOFSwitch sw, short inPort, short outPort) {
		
		// Initialize a packet out
		OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory()
				.getMessage(OFType.PACKET_OUT);
		po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		po.setInPort(inPort);
		
		// Set output actions
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(new OFActionOutput(outPort, (short) 0xffff));
		po.setActions(actions);
		po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
		
		// Set packet data and length
		byte[] packetData = packet.serialize();
		po.setPacketData(packetData);
		po.setLength((short) (OFPacketOut.MINIMUM_LENGTH + po.getActionsLength() + packetData.length));
		
		// Send packet
		try {
			sw.write(po, null);
			sw.flush();
		} catch (IOException e) {
			logger.error("Failure writing packet out", e);
		}
	}
	
	/**
	 * Performs load balancing based on a packet-in OpenFlow message for an 
	 * IPv4 packet destined for our logical load balancer.
	 */
	private void loadBalanceFlow(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		
		Server server = getNextServer();
		
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		
		// Create a flow table modification message to add a rule
    	OFFlowMod rule = new OFFlowMod();
		rule.setType(OFType.FLOW_MOD); 			
		rule.setCommand(OFFlowMod.OFPFC_ADD);
			
		// Create match 
		OFMatch match = new OFMatch()
			.setDataLayerDestination(LOAD_BALANCER_MAC)
			.setDataLayerSource(eth.getSourceMACAddress())
			.setDataLayerType(Ethernet.TYPE_IPv4)
			.setNetworkDestination(LOAD_BALANCER_IP)
			.setNetworkSource(((IPv4) eth.getPayload()).getSourceAddress())
			.setInputPort(pi.getInPort());
        
        // Set wildcards for Network protocol
		match.setWildcards(OFMatch.OFPFW_NW_PROTO);
		rule.setMatch(match);
			
		// Specify the timeouts for the rule
		rule.setIdleTimeout(IDLE_TIMEOUT);
		rule.setHardTimeout(HARD_TIMEOUT);
	        
	    // Set the buffer id to NONE -- implementation artifact
		rule.setBufferId(OFPacketOut.BUFFER_ID_NONE);
	       
        // Initialize list of actions
		ArrayList<OFAction> actions = new ArrayList<OFAction>();
		
		// Add action to re-write destination MAC to the MAC of the chosen server
		OFAction rewriteMAC = new OFActionDataLayerDestination(server.getMAC());
		actions.add(rewriteMAC);
		
		// Add action to re-write destination IP to the IP of the chosen server
		OFAction rewriteIP = new OFActionNetworkLayerDestination(server.getIP());
		actions.add(rewriteIP);
			
		// Add action to output packet
		OFAction outputTo = new OFActionOutput(server.getPort());
		actions.add(outputTo);
		
		// Add actions to rule
		rule.setActions(actions);
		short actionsLength = (short)(OFActionDataLayerDestination.MINIMUM_LENGTH
				+ OFActionNetworkLayerDestination.MINIMUM_LENGTH
				+ OFActionOutput.MINIMUM_LENGTH);
		
		// Specify the length of the rule structure
		rule.setLength((short) (OFFlowMod.MINIMUM_LENGTH + actionsLength));
		
		logger.debug("Actions length="+ (rule.getLength() - OFFlowMod.MINIMUM_LENGTH));
		
		logger.debug("Install rule for forward direction for flow: " + rule);
			
		try {
			sw.write(rule, null);
		} catch (Exception e) {
			e.printStackTrace();
		}	

		// Create a flow table modification message to add a rule for the reverse direction
    	OFFlowMod reverseRule = new OFFlowMod();
    	reverseRule.setType(OFType.FLOW_MOD); 			
    	reverseRule.setCommand(OFFlowMod.OFPFC_ADD);
			
		// Create match 
		OFMatch reverseMatch = new OFMatch()
			.setDataLayerSource(server.getMAC())
			.setDataLayerDestination(match.getDataLayerSource())
			.setDataLayerType(Ethernet.TYPE_IPv4)
			.setNetworkSource(server.getIP())
			.setNetworkDestination(match.getNetworkSource())
			.setInputPort(server.getPort());
        
		// Set wildcards for Network protocol
		reverseMatch.setWildcards(OFMatch.OFPFW_NW_PROTO);
		reverseRule.setMatch(reverseMatch);
			
		// Specify the timeouts for the rule
		reverseRule.setIdleTimeout(IDLE_TIMEOUT);
		reverseRule.setHardTimeout(HARD_TIMEOUT);
	        
	    // Set the buffer id to NONE -- implementation artifact
		reverseRule.setBufferId(OFPacketOut.BUFFER_ID_NONE);
	       
        // Initialize list of actions
		ArrayList<OFAction> reverseActions = new ArrayList<OFAction>();
		
		// Add action to re-write destination MAC to the MAC of the chosen server
		OFAction reverseRewriteMAC = new OFActionDataLayerSource(LOAD_BALANCER_MAC);
		reverseActions.add(reverseRewriteMAC);
		
		// Add action to re-write destination IP to the IP of the chosen server
		OFAction reverseRewriteIP = new OFActionNetworkLayerSource(LOAD_BALANCER_IP);
		reverseActions.add(reverseRewriteIP);
			
		// Add action to output packet
		OFAction reverseOutputTo = new OFActionOutput(pi.getInPort());
		reverseActions.add(reverseOutputTo);
		
		// Add actions to rule
		reverseRule.setActions(reverseActions);
		
		// Specify the length of the rule structure
		reverseRule.setLength((short) (OFFlowMod.MINIMUM_LENGTH
				+ OFActionDataLayerSource.MINIMUM_LENGTH
				+ OFActionNetworkLayerSource.MINIMUM_LENGTH
				+ OFActionOutput.MINIMUM_LENGTH));
		
		logger.debug("Install rule for reverse direction for flow: " + reverseRule);
			
		try {
			sw.write(reverseRule, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		pushPacket(sw, pi, actions, actionsLength);
	}
	
	/**
	 * Determines the next server to which a flow should be sent.
	 */
	private Server getNextServer() {
		lastServer = (lastServer + 1) % SERVERS.length;
		return SERVERS[lastServer];
	}

}

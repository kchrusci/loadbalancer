package pl.edu.agh.kt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;

public class Flows {

	private static final Logger logger = LoggerFactory.getLogger(Flows.class);
	
	public static short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 5; // in seconds
	public static short FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; // infinite
	public static short FLOWMOD_DEFAULT_PRIORITY = 100; 

	protected static boolean FLOWMOD_DEFAULT_MATCH_VLAN = true;
	protected static boolean FLOWMOD_DEFAULT_MATCH_MAC = true;
	protected static boolean FLOWMOD_DEFAULT_MATCH_IP_ADDR = true;
	protected static boolean FLOWMOD_DEFAULT_MATCH_TRANSPORT = true;
	
	public int i=0;

	public Flows() {
		logger.info("Flows() begin/end");
	}

	public static void sendPacketOut(IOFSwitch sw, OFPort inport, OFPort outport, FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		
		Ethernet l2 = new Ethernet(); //create a new l2 frame with LB's MAC
		l2.setSourceMACAddress(MacAddress.of("00:00:00:00:00:10")); //always the same
		
		//define variables independent of l3
		MacAddress srcMac = eth.getSourceMACAddress();
		MacAddress dstMac = eth.getDestinationMACAddress(); 
		
		//let's make it static
		TransportPort srcPort = 0;
		TransportPort dstPort = 0;
		
		if(eth.getEtherType()==EthType.IPv4){
		IPv4 ip = (IPv4) eth.getPayload();
		IPv4Address srcIp = ip.getSourceAddress();
		IPv4Address dstIp = ip.getDestinationAddress();
		IPv4 l3 = new IPv4(); //create a new l3 packet
		
			//port type nr neccessary to detect http requests and responses
			if (ip.getProtocol().equals(IpProtocol.TCP)) {
			TCP tcp = (TCP) ip.getPayload();
			srcPort = tcp.getSourcePort();
			dstPort = tcp.getDestinationPort();
			TCP l4 = new TCP();
			l3.setProtocol(IpProtocol.TCP);
			}
			//let's define port for UDP too
			else if (ip.getProtocol().equals(IpProtocol.UDP)) {
	        UDP udp = (UDP) ip.getPayload();
	        srcPort = udp.getSourcePort();
	        dstPort = udp.getDestinationPort();
			UDP l4 = new UDP();
			l3.setProtocol(IpProtocol.UDP);
			}
			//we have to put something if it's something else than TCP/UDP
			else{
				UDP l4 = new UDP();
				l3.setProtocol(IpProtocol.UDP);
			}
			//we never change source and destination ports -- they remain unmodified
			l4.setSourcePort(srcPort); 
			l4.setDestinationPort(dstPort);
			
			//if request to server -> swap destination address
			if((dstPort==80 || dstPort==8080) && dstIP==IPv4Address.of("192.168.1.10")){
				//0 1 2
				if(i%6<3){
					dstIP=IPv4Address.of("192.168.1.11");
				}
				//5
				else if(i%6=5){
					dstIP=IPv4Address.of("192.168.1.13");
				}
				//3 4
				else{
					dstIP=IPv4Address.of("192.168.1.12");
				}
				i++;
			}
		
		//specify l2 dstMac based on IP 
		if(dstIp == IPv4Address.of("192.168.1.1")){
			l2.setDestinationMACAddress(MacAddress.of("00:00:00:00:00:01"));
		}
		else if(dstIp == IPv4Address.of("192.168.1.2")){
			l2.setDestinationMACAddress(MacAddress.of("00:00:00:00:00:02"));
		}
		else if(dstIp == IPv4Address.of("192.168.1.3")){
			l2.setDestinationMACAddress(MacAddress.of("00:00:00:00:00:03"));
		}
		//we swapped IP from .10 to .1x, so it should do the trick
		else if(dstIp == IPv4Address.of("192.168.1.11")){
			l2.setDestinationMACAddress(MacAddress.of("00:00:00:00:00:11"));
		}
		else if(dstIp == IPv4Address.of("192.168.1.12")){
			l2.setDestinationMACAddress(MacAddress.of("00:00:00:00:00:12"));
		}
		else if(dstIp == IPv4Address.of("192.168.1.13")){
			l2.setDestinationMACAddress(MacAddress.of("00:00:00:00:00:13"));
		}
		
		//let's assume that it can be someting else and send it to broadcast
		else{
			l2.setDestinationMACAddress(MacAddress.of("ff:ff:ff:ff:ff:ff"));
		}
		
		l2.setEtherType(EthType.IPv4);
		//l2 finished
		
		l3.setDestinationAddress(dstIP);
		
		//we specified an IP protocol in TCP/UDP section so only srcIP should be swapped 
		//if needed -- that means that it is a response from one of "servers"
		if((srcPort==80 || srcPort==8080) && (srcIP==IPv4Address.of("192.168.1.11") 
				|| srcIP==IPv4Address.of("192.168.1.12") || srcIP==IPv4Address.of("192.168.1.13")){
			
			srcIP=IPv4Address.of("192.168.1.10");
		}

		l3.setSourceAddress(srcIP);
		l3.setTtl((byte) 64);
		
		} //this is the end for case: ethertype == IPv4
		
		//ARP
		else if(eth.getEtherType()==EthType.ARP){

		}
		else {
			
		}
		
		Data l7 = new Data();
		l7.setData(new byte[1000]);
		
		// set the payloads of each layer
		l2.setPayload(l3);
		l3.setPayload(l4);
		l4.setPayload(l7);
		
		// serialize
		byte[] serializedData = l2.serialize();
		
		// Create Packet-Out and Write to Switch
		OFPacketOut po = sw.getOFFactory()
		.buildPacketOut()
		.setData(serializedData)
		.setActions(Collections.singletonList((OFAction)sw.getOFFactory().actions().output(
		OFPort.FLOOD, 0xffFFffFF)))
		.setInPort(OFPort.CONTROLLER).build();
		sw.write(po);
	}

	public static void simpleAdd(IOFSwitch sw, OFPacketIn pin, FloodlightContext cntx, OFPort outPort) {
		// FlowModBuilder
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
		// match
		
		Match m = createMatchFromPacket(sw, pin.getInPort(), cntx);
		Match.Builder mb = sw.getOFFactory().buildMatch();
		mb.setExact(MatchField.IN_PORT, pin.getInPort());
		/*
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		if(eth.getEtherType()==EthType.IPv4){
		IPv4 ip = (IPv4) eth.getPayload();
		IPv4Address srcIp = ip.getSourceAddress();
		IPv4Address dstIp = ip.getDestinationAddress();
		
		if (FLOWMOD_DEFAULT_MATCH_IP_ADDR) {
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4).setExact(MatchField.IPV4_SRC, srcIp)
					.setExact(MatchField.IPV4_DST, dstIp);
		}
		}
		
		else if (eth.getEtherType() == EthType.ARP) { 

			mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
		}
		Match m = mb.build();*/

		
		// actions
		OFActionOutput.Builder aob = sw.getOFFactory().actions().buildOutput();
		List<OFAction> actions = new ArrayList<OFAction>();
		aob.setPort(outPort);
		aob.setMaxLen(Integer.MAX_VALUE);
		actions.add(aob.build());
		fmb.setMatch(m)
		.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
		.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
		.setBufferId(pin.getBufferId())
		.setOutPort(outPort)
		.setPriority(FLOWMOD_DEFAULT_PRIORITY);
		fmb.setActions(actions);
		
		// write flow to switch
		try {
		sw.write(fmb.build());
		logger.info("Flow from port {} forwarded to port {}; match: {}",
		new Object[] { pin.getInPort().getPortNumber(), outPort.getPortNumber(),
		m.toString() });
		} catch (Exception e) {
		logger.error("error {}", e);
		}
		
		
	}

	public static Match createMatchFromPacket(IOFSwitch sw, OFPort inPort, FloodlightContext cntx) {
		// The packet in match will only contain the port number.
		// We need to add in specifics for the hosts we're routing between.
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		VlanVid vlan = VlanVid.ofVlan(eth.getVlanID());
		MacAddress srcMac = eth.getSourceMACAddress();
		MacAddress dstMac = eth.getDestinationMACAddress();

		Match.Builder mb = sw.getOFFactory().buildMatch();
		mb.setExact(MatchField.IN_PORT, inPort);

		if (FLOWMOD_DEFAULT_MATCH_MAC) {
			mb.setExact(MatchField.ETH_SRC, srcMac).setExact(MatchField.ETH_DST, dstMac);
		}

		if (FLOWMOD_DEFAULT_MATCH_VLAN) {
			if (!vlan.equals(VlanVid.ZERO)) {
				mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlanVid(vlan));
			}
		}
		
		
		
		// TODO Detect switch type and match to create hardware-implemented flow
		if (eth.getEtherType() == EthType.IPv4) { /*
													 * shallow check for
													 * equality is okay for
													 * EthType
													 */
			IPv4 ip = (IPv4) eth.getPayload();
			IPv4Address srcIp = ip.getSourceAddress();
			IPv4Address dstIp = ip.getDestinationAddress();

			if (FLOWMOD_DEFAULT_MATCH_IP_ADDR) {
				mb.setExact(MatchField.ETH_TYPE, EthType.IPv4).setExact(MatchField.IPV4_SRC, srcIp)
						.setExact(MatchField.IPV4_DST, dstIp);
			}

			if (FLOWMOD_DEFAULT_MATCH_TRANSPORT) {
				/*
				 * Take care of the ethertype if not included earlier, since
				 * it's a prerequisite for transport ports.
				 */
				if (!FLOWMOD_DEFAULT_MATCH_IP_ADDR) {
					mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
				}

				if (ip.getProtocol().equals(IpProtocol.TCP)) {
					TCP tcp = (TCP) ip.getPayload();
					mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP).setExact(MatchField.TCP_SRC, tcp.getSourcePort())
							.setExact(MatchField.TCP_DST, tcp.getDestinationPort());
				} else if (ip.getProtocol().equals(IpProtocol.UDP)) {
					UDP udp = (UDP) ip.getPayload();
					mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP).setExact(MatchField.UDP_SRC, udp.getSourcePort())
							.setExact(MatchField.UDP_DST, udp.getDestinationPort());
				}
			}
		} else if (eth.getEtherType() == EthType.ARP) { /*
														 * shallow check for
														 * equality is okay for
														 * EthType
														 */
			mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
		}

		return mb.build();
	}
}

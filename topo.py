"""Custom topology example
Two directly connected switches plus a host for each switch:
host --- switch --- switch --- host
Adding the 'topos' dict with a key/value pair to generate our newly defined
topology enables one to pass in '--topo=mytopo' from the command line.
"""
from mininet.topo import Topo
from mininet.net import Mininet

class MyTopo(Topo):

    def __init__(self):

        Topo.__init__(self)

        # Add hosts and switches
        host1 = net.addHost('h1', ip='192.168.1.1')
        host2 = net.addHost('h2', ip='192.168.1.2')
        host3 = net.addHost('h3', ip='192.168.1.3')
        server1 = net.addHost('s1', ip='192.168.1.11')
        server2 = net.addHost('s2', ip='192.168.1.12')
        server3 = net.addHost('s3', ip='192.168.1.13')

        lb = net.addSwitch('lb')

        # Add links
        net.addLink(host1, lb)
        net.addLink(host2, lb)
        net.addLink(host3, lb)
        net.addLink(lb, server1)
        net.addLink(lb, server2)
        net.addLink(lb, server3)


topos = {'mytopo': (lambda: MyTopo())}

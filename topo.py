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
        host1 = self.addHost('h1',
                              ip='192.168.1.1',
                              mac='00:00:00:00:00:01')
        host2 = self.addHost('h2',
                              ip='192.168.1.2',
                              mac='00:00:00:00:00:02')
        host3 = self.addHost('h3',
                             ip='192.168.1.3',
                             mac='00:00:00:00:00:03')

        server1 = self.addHost('s1',
                                ip='192.168.1.11',
                                mac='00:00:00:00:00:11')
        server2 = self.addHost('s2',
                                ip='192.168.1.12',
                                mac='00:00:00:00:00:12')
        server3 = self.addHost('s3', ip='192.168.1.13', mac='00:00:00:00:00:13')

        lb = self.addSwitch('c1', mac='00:00:00:00:00:10')

        # Add links
        self.addLink(host1, lb)
        self.addLink(host2, lb)
        self.addLink(host3, lb)
        self.addLink(lb, server1)
        self.addLink(lb, server2)
        self.addLink(lb, server3)


topos = {'mytopo': (lambda: MyTopo())}

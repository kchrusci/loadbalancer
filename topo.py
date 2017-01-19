"""Custom topology example
Two directly connected switches plus a host for each switch:
host --- switch --- switch --- host
Adding the 'topos' dict with a key/value pair to generate our newly defined
topology enables one to pass in '--topo=mytopo' from the command line.
"""
from mininet.topo import Topo


class MyTopo(Topo):

    def __init__(self):

        Topo.__init__(self)

        # Add hosts and switches
        host1 = self.addHost('h1')
        host2 = self.addHost('h2')
        host3 = self.addHost('h3')
        server1 = self.addHost('s1')
        server2 = self.addHost('s2')
        server3 = self.addHost('s3')

        lb = self.addSwitch('lb')

        # Add links
        self.addLink(host1, lb)
        self.addLink(host2, lb)
        self.addLink(host3, lb)
        self.addLink(lb, server1)
        self.addLink(lb, server2)
        self.addLink(lb, server3)


topos = {'mytopo': (lambda: MyTopo())}

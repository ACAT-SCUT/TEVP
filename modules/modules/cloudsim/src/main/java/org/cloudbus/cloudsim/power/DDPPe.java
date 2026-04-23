package org.cloudbus.cloudsim.power;

/**DDP算法专属的Pe结构*/
public class DDPPe {
    public double MIPs_Cap;//Pe的MIPs容量
    public double MIPs_available;//Pe的可用MIPs

    public DDPPe(double MIPs_Cap, double MIPs_available) {
        this.MIPs_Cap = MIPs_Cap;
        this.MIPs_available = MIPs_available;
    }
}

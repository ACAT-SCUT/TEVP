package org.cloudbus.cloudsim.power;

//这个类专门用来描述Vm的各个vCPU分配给Host的哪个Pe，并且实际申请到了多少的MIPs
//每个Vm都会有一个DDPPeMipsMapPair的数组（用于记录自己的vCPU映射到了host的那些Pe上）
public class DDPPeMipsMapPair {
    int PeId;
    double PeMipsReq;

    DDPPeMipsMapPair(int PeId, double PeMipsReq) {
        this.PeId = PeId;
        this.PeMipsReq = PeMipsReq;
    }
}

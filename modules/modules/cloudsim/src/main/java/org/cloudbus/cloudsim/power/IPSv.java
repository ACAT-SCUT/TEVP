package org.cloudbus.cloudsim.power;

import java.util.HashMap;

//IPSO专用的解向量，IPSO的解为二维，第一维用于表示Host是否开启，
// 第二维则记录VM在相应维数的Host上的放置情况
public class IPSv {
    public HashMap<Integer, Integer> FirstDimension = new HashMap<>();//<HostId,0或1>
    public DDPSv SecondDimension;//这里我用DDPSv作为第二维，里面包含了Fitness和VmList

    //构造函数（浅拷贝）
    public IPSv(DDPSv sv) {
        //初始化第一维
        for (Integer HostId : sv.HostList.keySet()) {
            if (sv.HostList.get(HostId).VmList.size() > 0) FirstDimension.put(HostId, 1);
            else FirstDimension.put(HostId, 0);
        }
        //初始化第二维
        SecondDimension = sv;//浅拷贝
    }

    //构造函数（深拷贝）
    public IPSv(IPSv sv, boolean isVmListVmAlreadyInHostList) {
        for (Integer HostId : sv.FirstDimension.keySet()) {
            FirstDimension.put(HostId, sv.FirstDimension.get(HostId));
        }
        SecondDimension = new DDPSv(sv.SecondDimension, isVmListVmAlreadyInHostList);
    }

}

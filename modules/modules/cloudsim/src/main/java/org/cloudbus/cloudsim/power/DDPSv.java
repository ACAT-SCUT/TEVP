package org.cloudbus.cloudsim.power;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 解向量，用于记录相关的数据
 */
public class DDPSv {
    //解向量的Host状态，key为HostId
    public HashMap<Integer, DDPHost> HostList;
    //解向量的待迁移Vm，key为VmId（该VmList在调用DiscreteDEPSO时就确定了）
    public HashMap<Integer, DDPVm> VmList;
    public double Fitness; //适应值
    //默认构造函数
    public DDPSv() {
        HostList = new HashMap<>();
        VmList = new HashMap<>();
    }

    //构造函数，负责复制HostList和VmList的数据到自身的
    //isDeepCopy==true为深拷贝，isDeepCopy==false为浅拷贝
    //PS：调用该函数的地方，要求传入VList的Vm一定还没放置到HList中
    public DDPSv(ArrayList<DDPHost> HList, ArrayList<DDPVm> VList, boolean isDeepCopy) {
        HostList = new HashMap<>(HList.size());
        VmList = new HashMap<>(VList.size());

        //将List的数据复制到新的List上面
        for (DDPHost host : HList) {
            if (!isDeepCopy) {
                HostList.put(host.HostId, host);
            } else {
                DDPHost tmp = new DDPHost(host, true);//深拷贝
                HostList.put(tmp.HostId, tmp);
            }
        }
        if (!isDeepCopy) {
            for (DDPVm vm : VList) {
                VmList.put(vm.VmId, vm);
            }
        } else {
            //注意，DDPSv的VmList是待放置的Vm列表，不是Host完整的Vm列表啊，现在HostList里面根本就没有这部分Vm
            for (DDPVm vm : VList) {
                DDPVm tmp = new DDPVm(vm);
                VmList.put(tmp.VmId, tmp);//获取host深拷贝时创建的Vm
            }
        }

    }

    //复制构造函数（深拷贝），这里分为两种情况：
    // （1）sv.VmList的Vm还没放置到sv.HostList中的情况，这里只需要根据sv.VmList进行新VM的深拷贝即可
    // （2）sv.VmList的Vm已经放置到了sv.HostList中的情况，这里需要将sv.HostList里面新创建的Vm对象赋值给sv.VmList
    public DDPSv(DDPSv sv, boolean isVmListVmAlreadyInHostList) {
        ArrayList<DDPHost> HList = new ArrayList<>();
        ArrayList<DDPVm> VList = new ArrayList<>();
        for (Integer HostId : sv.HostList.keySet()) {
            HList.add(sv.HostList.get(HostId));
        }
        for (Integer VmId : sv.VmList.keySet()) {
            VList.add(sv.VmList.get(VmId));
        }

        HostList = new HashMap<>(HList.size());
        VmList = new HashMap<>(VList.size());
        //将List的数据复制到新的List上面
        for (DDPHost host : HList) {
            DDPHost tmp = new DDPHost(host, true);//深拷贝
            HostList.put(tmp.HostId, tmp);
        }
        if (!isVmListVmAlreadyInHostList) {
            for (DDPVm vm : VList) {
                DDPVm tmp = new DDPVm(vm);
                VmList.put(tmp.VmId, tmp);//获取host深拷贝时创建的Vm
            }
        }else{//如果VList的vm本来已经放置在了HostList的Host中，则sv.VmList的vm必须是上面深拷贝Host中新创建的Vm
            for(DDPVm vm:VList){
                //Debug
                if(HostList.get(vm.Host_Belongs).VmList.get(vm.VmId)==null){
                    System.out.println("DDPSv复制构造函数有误，拷贝的Sv中存在相应的Host_Belongs上" +
                            "找不到相应Vm的情况，算法有误！！");
                    System.exit(1);
                }
                //这里的HostBelongs一定是准确的，因为进入这个分支的调用Vm已经准确放置到Host上了
                VmList.put(vm.VmId,HostList.get(vm.Host_Belongs).VmList.get(vm.VmId));
            }
        }
        Fitness = sv.Fitness;
    }
}

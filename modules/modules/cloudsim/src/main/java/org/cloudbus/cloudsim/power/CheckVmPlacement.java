package org.cloudbus.cloudsim.power;

import java.util.ArrayList;
import java.util.HashMap;

public class CheckVmPlacement {
    //尝试进行Vm放置，放置成功就返回新的result，result里面包括"PeMipsMap","total_cpu","ram","bw"，
    // "PeMipsMap"为vm在host上的新的Pe映射列表；
    // "total_cpu"为vm申请到的总MIPs数；
    // "ram"为vm申请到的总RAM资源量；
    // "bw"为vm申请到的总带宽资源量；
    // 若放置失败，败就回滚host上相应Pe的数据，并返回null
    // 注意：1、如果放置成功，放置过程中host上PeList的数据是会被该函数更新的！！！！！
    //       2、RAM和BW资源之所以要用Allocate作为判断标准，是因为优化算法不涉及RAM和BW资源，
    //          不管RAM和BW是否发生性能退化，直接用Allocate的量进行VM放置
    public static HashMap<String, Object> isSuitableForVm(DDPHost host, DDPVm vm) {
        HashMap<String, Object> result = null;
        double Total_Allocate_MIPs = 0;
        //先检查RAM、Band、Total_Cpu、Core数目
        if (vm.Total_CPU_Request <= (host.UP_THR - host.CPU_util) * host.CPU_Cap //&& vm.requiredNumOfPes <= host.PeList.size()
                && vm.MipsCapacityOfSingleVcpu <= host.PeList.get(0).MIPs_Cap
                && vm.RAM_Allocate <= host.RAM_avail && vm.BW_Allocate <= host.BW_avail) {
            //接下来检查CPU，由于存在Host多个Pe，所以逐个Pe检查，并将放置好的vCPU记录到PeMipsMap里面
            ArrayList<DDPPeMipsMapPair> PeMipsMap = new ArrayList<>();
            int vCpuPlaceCounter = 0;//放到第几个vCPU的Mips请求
            while (vCpuPlaceCounter < vm.MIPs_Request.size()) {
                //遍历Pe，尽可能把MIPs放置在上面，放不了就尝试下一个Pe，还是不行就结束Vm放置
                //（虽然可能会产生碎片，但是总比CloudSim硬把一个vCPU放到两个Pe上要好）
                int PeId;
                for (PeId = 0; PeId < host.PeList.size(); PeId++) {
                    //检查vCPU的MIPs请求量是否少于Pe的MIPs余量，如果少于，则可以放置该vCPU
                    if (vm.MIPs_Request.get(vCpuPlaceCounter)
                            <= host.PeList.get(PeId).MIPs_available) {
                        Total_Allocate_MIPs += vm.MIPs_Request.get(vCpuPlaceCounter);//统计总分配的MIPs数
                        host.PeList.get(PeId).MIPs_available -=
                                vm.MIPs_Request.get(vCpuPlaceCounter);//相应Pe减去相应的MIPs
                        DDPPeMipsMapPair tmpCb = new DDPPeMipsMapPair//创建DDPVmCoreBelongs对象
                                (PeId, vm.MIPs_Request.get(vCpuPlaceCounter));
                        PeMipsMap.add(tmpCb);
                        vCpuPlaceCounter++;
                        break;
                    }
                }
                if (PeId == host.PeList.size()) {//这种情况说明，不存在任何一个Pe能够放得下该趟循环中的vCPU，Vm在尝试放置失败
                    break;
                }
            }
            //放置成功就把cpu、ram、bw的结果都塞进来
            if (vCpuPlaceCounter == vm.MIPs_Request.size()) {
                result = new HashMap<>();
                result.put("PeMipsMap", PeMipsMap);
                result.put("total_cpu", Total_Allocate_MIPs);
                result.put("ram", vm.RAM_Allocate);
                result.put("bw", vm.BW_Allocate);
            } else {
                //Vm放置失败，host的PeList回滚
                for (DDPPeMipsMapPair pair : PeMipsMap) {
                    host.PeList.get(pair.PeId).MIPs_available += pair.PeMipsReq;
                }
            }
        }
        return result;
    }

    //Debug：用于检查为什么会回填失败
    public static HashMap<String, Object> isSuitableForVmTest(DDPHost host, DDPVm vm) {
        HashMap<String, Object> result = null;
        double Total_Allocate_MIPs = 0;
        //先检查RAM、Band、Total_Cpu、Core数目
        if (vm.Total_CPU_Request <= (host.UP_THR - host.CPU_util) * host.CPU_Cap //&& vm.requiredNumOfPes <= host.PeList.size()
                && vm.MipsCapacityOfSingleVcpu <= host.PeList.get(0).MIPs_Cap
                && vm.RAM_Allocate <= host.RAM_avail && vm.BW_Allocate <= host.BW_avail) {
            //接下来检查CPU，由于存在Host多个Pe，所以逐个Pe检查，并将放置好的vCPU记录到PeMipsMap里面
            ArrayList<DDPPeMipsMapPair> PeMipsMap = new ArrayList<>();
            int vCpuPlaceCounter = 0;//放到第几个vCPU的Mips请求
            while (vCpuPlaceCounter < vm.MIPs_Request.size()) {
                //遍历Pe，尽可能把MIPs放置在上面，放不了就尝试下一个Pe，还是不行就结束Vm放置
                //（虽然可能会产生碎片，但是总比CloudSim硬把一个vCPU放到两个Pe上要好）
                int PeId;
                for (PeId = 0; PeId < host.PeList.size(); PeId++) {
                    //检查vCPU的MIPs请求量是否少于Pe的MIPs余量，如果少于，则可以放置该vCPU
                    if (vm.MIPs_Request.get(vCpuPlaceCounter)
                            <= host.PeList.get(PeId).MIPs_available) {
                        Total_Allocate_MIPs += vm.MIPs_Request.get(vCpuPlaceCounter);//统计总分配的MIPs数
                        host.PeList.get(PeId).MIPs_available -=
                                vm.MIPs_Request.get(vCpuPlaceCounter);//相应Pe减去相应的MIPs
                        DDPPeMipsMapPair tmpCb = new DDPPeMipsMapPair//创建DDPVmCoreBelongs对象
                                (PeId, vm.MIPs_Request.get(vCpuPlaceCounter));
                        PeMipsMap.add(tmpCb);
                        vCpuPlaceCounter++;
                        break;
                    }
                }
                if (PeId == host.PeList.size()) {//这种情况说明，不存在任何一个Pe能够放得下该趟循环中的vCPU，Vm在尝试放置失败
                    break;
                }
            }
            //放置成功就把cpu、ram、bw的结果都塞进来
            if (vCpuPlaceCounter == vm.MIPs_Request.size()) {
                result = new HashMap<>();
                result.put("PeMipsMap", PeMipsMap);
                result.put("total_cpu", Total_Allocate_MIPs);
                result.put("ram", vm.RAM_Allocate);
                result.put("bw", vm.BW_Allocate);
            } else {
                //Vm放置失败，host的PeList回滚
                for (DDPPeMipsMapPair pair : PeMipsMap) {
                    host.PeList.get(pair.PeId).MIPs_available += pair.PeMipsReq;
                }
                //Debug信息输出
                System.out.println("每个Pe的MIPs余量为：");
                double sumPeAvailableMips=0;
                for(DDPPe pe:host.PeList){
                    sumPeAvailableMips+=pe.MIPs_available;
                    System.out.println(pe.MIPs_available);
                }
                double sumVmRequestMips=0;
                System.out.println("Vm的每个Vcpu的Mips请求量为：");
                for(Double val:vm.MIPs_Request){
                    sumVmRequestMips+=val;
                    System.out.println(val);
                }
                System.out.println("Pe的MIPs总余量："+sumPeAvailableMips);
                System.out.println("Vm的MIPs总请求量："+sumVmRequestMips);
            }
        }
        return result;
    }
}

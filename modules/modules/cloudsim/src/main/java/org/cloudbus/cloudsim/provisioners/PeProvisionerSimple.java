/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.provisioners;

import org.cloudbus.cloudsim.Vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PeProvisionerSimple is an extension of {@link PeProvisioner} which uses a best-effort policy to
 * allocate virtual PEs to VMs:
 * if there is available mips on the physical PE, it allocates to a virtual PE; otherwise, it fails.
 * Each host's PE has to have its own instance of a PeProvisioner.
 *
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 2.0
 */
//每个Pe都会有一个PeProvisioner，用于管理该Pe上的Mips对Vm的分配操作
//Pe比较特殊，超分策略不在这里实现，在VmSchedulerTimeSharedOverSubscription中实现
public class PeProvisionerSimple extends PeProvisioner {

    /**
     * The PE map, where each key is a VM id and each value
     * is the list of PEs (in terms of their amount of MIPS)
     * allocated to that VM.
     */
    //<Vmid,MIPs列表>，同一个VmId可能会反复出现在PeTable里面，因为VmSchedulerTimeShared就是这样搞的
    private Map<String, List<Double>> peTable;

    /**
     * Instantiates a new pe provisioner simple.
     *
     * @param availableMips The total mips capacity of the PE that the provisioner can allocate to VMs.
     * @pre $none
     * @post $none
     */
    public PeProvisionerSimple(double availableMips) {
        super(availableMips);
        setPeTable(new HashMap<String, ArrayList<Double>>());
    }

    @Override
    //分配mips给特定Vm，注意，传参进来的mips不是VM的所有mips，仅仅是一个VM其中一个vCPU的mips而已
    public boolean allocateMipsForVm(Vm vm, double mips) {
        return allocateMipsForVm(vm.getUid(), mips);
    }

    @Override
    //分配mips给特定Vm的某个vCPU，注意，传参进来的mips不是VM的所有mips，仅仅是一个VM其中一个vCPU的mips而已
    public boolean allocateMipsForVm(String vmUid, double mips) {
        //这里原先很有毒，竟然return false，你叫超分的人情何以堪
        // （但是按道理来说，VmSchedulerOverSubscription应该已经控制了相关的请求数据，
        // 现在改回return false又没有出错了）
        if (getAvailableMips() < mips || getAvailableMips() == 0) {//修改点
            //mips=getAvailableMips();//修改点
            return false;
        }

        List<Double> allocatedMips;

        if (getPeTable().containsKey(vmUid)) {
            allocatedMips = getPeTable().get(vmUid);//如果原先该vm已经向该Pe申请了资源，自然在List<Double>后面继续add就好了
        } else {
            allocatedMips = new ArrayList<Double>();
        }

        allocatedMips.add(mips);

        setAvailableMips(getAvailableMips() - mips);
        getPeTable().put(vmUid, allocatedMips);

        return true;
    }

    @Override
    //分配多个核的mips给特定Vm
    public boolean allocateMipsForVm(Vm vm, List<Double> mips) {
        int totalMipsToAllocate = 0;
        for (double _mips : mips) {
            totalMipsToAllocate += _mips;
        }

        //剩余的Mips+已分配给该Vm的Mips要少于该Vm的Mips请求总量
        if (getAvailableMips() + getTotalAllocatedMipsForVm(vm) < totalMipsToAllocate) {
            return false;
        }

        setAvailableMips(getAvailableMips() + getTotalAllocatedMipsForVm(vm) - totalMipsToAllocate);

        getPeTable().put(vm.getUid(), mips);

        return true;
    }

    @Override
    //收回所有Mips
    public void deallocateMipsForAllVms() {
        super.deallocateMipsForAllVms();
        getPeTable().clear();
    }

    @Override
    public double getAllocatedMipsForVmByVirtualPeId(Vm vm, int peId) {
        if (getPeTable().containsKey(vm.getUid())) {
            try {
                return getPeTable().get(vm.getUid()).get(peId);
            } catch (Exception e) {
            }
        }
        return 0;
    }

    @Override
    public List<Double> getAllocatedMipsForVm(Vm vm) {
        if (getPeTable().containsKey(vm.getUid())) {
            return getPeTable().get(vm.getUid());
        }
        return null;
    }

    @Override
    public double getTotalAllocatedMipsForVm(Vm vm) {
        if (getPeTable().containsKey(vm.getUid())) {
            double totalAllocatedMips = 0.0;
            for (double mips : getPeTable().get(vm.getUid())) {
                totalAllocatedMips += mips;
            }
            return totalAllocatedMips;
        }
        return 0;
    }

    @Override
    public void deallocateMipsForVm(Vm vm) {
        if (getPeTable().containsKey(vm.getUid())) {
            for (double mips : getPeTable().get(vm.getUid())) {
                setAvailableMips(getAvailableMips() + mips);
            }
            getPeTable().remove(vm.getUid());
        }
    }

    /**
     * Gets the pe map.
     *
     * @return the pe map
     */
    protected Map<String, List<Double>> getPeTable() {
        return peTable;
    }

    //这是一个获取该Pe分配给特定Vm的MIPs数的函数
    public List<Double> getPeTableForVm(Vm vm) {
        List<Double> allocatedMips;
        //这个函数一定能获取到MIPs
        allocatedMips = getPeTable().get(vm.getUid());
        return allocatedMips;
    }

    public List<Double> getPeTableByVmUid(String VmUid) {
        List<Double> allocatedMips;
        //这个函数一定能获取到MIPs
        allocatedMips = getPeTable().get(VmUid);
        return allocatedMips;
    }


    /**
     * Sets the pe map.
     *
     * @param peTable the peTable to set
     */
    @SuppressWarnings("unchecked")
    protected void setPeTable(Map<String, ? extends List<Double>> peTable) {
        this.peTable = (Map<String, List<Double>>) peTable;
    }

}

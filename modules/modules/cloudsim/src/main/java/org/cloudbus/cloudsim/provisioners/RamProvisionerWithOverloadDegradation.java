/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.provisioners;

import org.cloudbus.cloudsim.Vm;

import java.util.HashMap;
import java.util.Map;

/**
 * 这个策略的话其实是和我的算法相关，本来实际应用中，RAM、Bandwidth这些都是有利用率的，
 * 但是现在全部给Vm分配Vm资源容量（RAM_CAP）的RAM数量，导致我的算法根本不能好好地运行，
 * 所以只能给它写个性能退化策略了
 *
 * @author CHUNKI LI
 * @since CloudSim Toolkit 4.0
 */
public class RamProvisionerWithOverloadDegradation extends RamProvisioner {

    /**
     * The RAM map, where each key is a VM id and each value
     * is the amount of RAM allocated to that VM.
     */
    private Map<String, Vm> VmList;//修改点：新增记录该Host上的Vm，性能退化要用到
    private Map<String, Integer> ramRequestTable;//不同Vm请求的Ram资源数量
    private Map<String, Integer> ramAllocateTable;//实际分配给每台Vm的Ram资源数量


    /**
     * Instantiates a new ram provisioner simple.
     *
     * @param availableRam The total ram capacity from the host that the provisioner can allocate to VMs.
     */
    public RamProvisionerWithOverloadDegradation(int availableRam) {
        super(availableRam);
        setRamAllocateTable(new HashMap<String, Integer>());
        //修改点：新增：同步维护ramRequestTable和VmList
        ramRequestTable = new HashMap<>();
        VmList=new HashMap<>();
    }

    @Override
    //由于是OverSubscription，因此肯定能够成功分配Ram资源给不同的Vm
    //注意传进来的ram必须是vm在某一时刻申请的ram
    public boolean allocateRamForVm(Vm vm, int ram) {
        //先更新ramRequestTable
        ramRequestTable.put(vm.getUid(), ram);
        VmList.put(vm.getUid(), vm);//修改点：维护VmList列表
        //尝试分配Ram资源，如果资源充足就直接分配资源
        if (ram <= getAvailableRam()) {
            setAvailableRam(getAvailableRam() - ram);
            getRamAllocateTable().put(vm.getUid(), ram);
            vm.setCurrentAllocatedRam(getAllocatedRamForVm(vm));
        } else {//如果剩余的资源不足则进行超分
            ramAllocateTable.clear();
            long sumRequest = 0;//统计目前Host上所有Vm请求的ram数目
            for (String VmId : ramRequestTable.keySet()) {
                sumRequest += ramRequestTable.get(VmId);
            }
            for (String VmId : ramRequestTable.keySet()) {//按请求的比例分配ram
                int allocateRam = (int) Math.floor(getRam() * (ramRequestTable.get(VmId) / sumRequest));
                ramAllocateTable.put(VmId, allocateRam);
                VmList.get(VmId).setCurrentAllocatedRam(allocateRam);
            }
            setAvailableRam(0);
        }
        return true;
        /*
        int maxRam = vm.getRam();
                //If the requested amount of RAM to be allocated to the VM is greater than
                //the amount of VM is in fact requiring, allocate only the
                //amount defined in the Vm requirements.
        if (ram >= maxRam) {
            ram = maxRam;
        }

        deallocateRamForVm(vm);

        if (getAvailableRam() >= ram) {
            setAvailableRam(getAvailableRam() - ram);
            getRamTable().put(vm.getUid(), ram);
            vm.setCurrentAllocatedRam(getAllocatedRamForVm(vm));
        } else {//有待改进：超分一下比较好，不然的话有可能有的Vm资源很充足，有的Vm一点资源都拿不到
            int availRam = this.getAvailableRam();
            this.setAvailableRam(0);
            this.getRamAllocateTable().put(vm.getUid(), availRam);
            vm.setCurrentAllocatedRam(this.getAllocatedRamForVm(vm));
            Log.printLine("[RamOverProvisioner] VM #" + vm.getId() + "'s Ram is under-provisioned, " + availRam + '/' + ram);
        }

        return true;*/
    }

    @Override
    public int getAllocatedRamForVm(Vm vm) {
        if (getRamAllocateTable().containsKey(vm.getUid())) {
            return getRamAllocateTable().get(vm.getUid());
        }
        return 0;
    }

    @Override
    //由于是超分，所以这里也要重写，先检查是不是发生超载性能退化了，如果超分了就移除Vm并重新分配所有的RAM
    public void deallocateRamForVm(Vm vm) {
        if (getRamAllocateTable().containsKey(vm.getUid())) {
            //修改点：新增：维护ramRequestTable和VmList（并且性能退化）
            ramRequestTable.remove(vm.getUid());
            VmList.remove(vm.getUid());
            //考虑性能退化，所以每次deallocate都要重新分配资源
            ramAllocateTable.clear();
            setAvailableRam(getRam());
            vm.setCurrentAllocatedRam(0);

            //重新分配RAM资源给Vm（并根据目前剩余Vm的RAM请求总量，判断是否会继续发生超载性能退化）
            long sumRequest = 0;//统计目前Host上所有剩余Vm请求的ram数目
            for (String VmId : ramRequestTable.keySet()) {
                sumRequest += ramRequestTable.get(VmId);
            }
            if (sumRequest < getRam()) {//没有性能退化就逐个Vm放置即可
                for (String VmId : ramRequestTable.keySet()) {
                    allocateRamForVm(VmList.get(VmId),ramRequestTable.get(VmId));
                }
            } else {//如果仍然超分那么直接在这里进行超分放置（不要用allocateRamForVm，省时间）
                for (String VmId : ramRequestTable.keySet()) {//按请求的比例分配ram
                    int allocateRam = (int) Math.floor(getRam() * (ramRequestTable.get(VmId) / sumRequest));
                    ramAllocateTable.put(VmId, allocateRam);
                    VmList.get(VmId).setCurrentAllocatedRam(allocateRam);
                }
                setAvailableRam(0);
            }
        }
    }

    @Override
    public void deallocateRamForAllVms() {
        super.deallocateRamForAllVms();
        getRamAllocateTable().clear();
        //修改点：新增：同步维护ramRequestTable和VmList
        ramRequestTable.clear();
        VmList.clear();
    }

    @Override
    // 修改点：我的调度算法用不上这个函数，并且Vm的Ram数目是一定能够分配的，
    // 因为这是OverSubscription（资源不够的时候会自动按照一定的比例降低每个Vm申请到的RAM数量），
    // 所以直接return true就好了
    public boolean isSuitableForVm(Vm vm, int ram) {
        /*//获取已经分配了多少MIPs给Vm，用于等一下恢复原状的，因为真正的Ram分配不在这里进行
        int allocatedRam = getAllocatedRamForVm(vm);
        boolean result = allocateRamForVm(vm, ram);//尝试放一下看行不行
        deallocateRamForVm(vm);
        if (allocatedRam >= 0) {//修改点：大于改成大于等于
            allocateRamForVm(vm, allocatedRam);
        }
        return result;*/
        return true;
    }

    /**
     * Gets the map between VMs and allocated ram.
     *
     * @return the ram map
     */
    protected Map<String, Integer> getRamAllocateTable() {
        return ramAllocateTable;
    }

    /**
     * Sets the map between VMs and allocated ram.
     *
     * @param ramAllocateTable the ram map
     */
    protected void setRamAllocateTable(Map<String, Integer> ramAllocateTable) {
        this.ramAllocateTable = ramAllocateTable;
    }

}

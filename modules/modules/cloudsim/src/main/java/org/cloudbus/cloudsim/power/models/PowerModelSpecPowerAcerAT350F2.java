package org.cloudbus.cloudsim.power.models;

public class PowerModelSpecPowerAcerAT350F2 extends PowerModelSpecPower {
    /**
     * The power consumption and peff are according to the utilization percentage.
     * power的单位是（W），peff的单位是（ssj_ops）
     */
    private final double[] power = { 103, 147, 167, 190, 218, 245, 273, 296, 324, 378, 409};
    private final double[] peff = { 0, 919, 1618, 2138, 2478, 2758, 2983, 3187, 3352, 3221, 3298};
    private final double PeakPower = 409;//峰值能耗
    private final double PeakPeff = 3352;//峰值效能比
    private final double pp_CPU_util = 0.8;//峰值效能比下的CPU利用率

    @Override
    protected double getPowerData(int index) {return power[index];}

    @Override
    protected double getPeffData(int index) {return peff[index];}

    @Override
    public final double[] getPowerList() {return power;}

    @Override
    public final double[] getPeffList() {return peff;}

    @Override
    public double getPp_CPU_tuil(){return pp_CPU_util;}

    @Override
    public double getPeakPower() {return PeakPower;}

    @Override
    public double getPeakPeff() {return PeakPeff;}
}


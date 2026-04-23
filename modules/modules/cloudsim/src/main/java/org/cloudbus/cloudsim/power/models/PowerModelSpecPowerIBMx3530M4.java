package org.cloudbus.cloudsim.power.models;

public class PowerModelSpecPowerIBMx3530M4 extends PowerModelSpecPower {
    /**
     * The power consumption and peff are according to the utilization percentage.
     * power的单位是（W），peff的单位是（ssj_ops）
     */
    private final double[] power = { 62.6, 83.6, 93.4, 103, 114, 129, 149, 173, 199, 232, 274};
    private final double[] peff = { 0, 1765, 3146, 4260, 5145, 5676, 5923, 5942, 5897, 5708, 5348};
    private final double PeakPower = 274;//峰值能耗
    private final double PeakPeff = 5942;//峰值效能比
    private final double pp_CPU_util = 0.7;//峰值效能比下的CPU利用率

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


package org.cloudbus.cloudsim.power.models;

public class PowerModelSpecPowerDellPowerEdgeR240 extends PowerModelSpecPower {
    /**
     * The power consumption and peff are according to the utilization percentage.
     * power的单位是（W），peff的单位是（ssj_ops）
     */
    private final double[] power = { 20.0, 25.1, 28.5, 32.3, 36.6, 41.7, 47.5, 55.9, 67.2, 81.0, 93.1};
    private final double[] peff = { 0, 3408, 6021, 7965, 9358, 10289, 10800, 10762, 10228, 9499, 9208};
    private final double PeakPower = 93.1;//峰值能耗
    private final double PeakPeff = 10800;//峰值效能比
    private final double pp_CPU_util = 0.6;//峰值效能比下的CPU利用率

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


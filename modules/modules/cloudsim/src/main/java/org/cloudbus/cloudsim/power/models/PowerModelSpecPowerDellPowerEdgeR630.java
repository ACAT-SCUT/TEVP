package org.cloudbus.cloudsim.power.models;

public class PowerModelSpecPowerDellPowerEdgeR630 extends PowerModelSpecPower {
    /**
     * The power consumption and peff are according to the utilization percentage.
     * power的单位是（W），peff的单位是（ssj_ops）
     */
    private final double[] power = { 51.2, 93.4, 111, 129, 146, 162, 176, 197, 223, 255, 287};
    private final double[] peff = { 0, 3473, 5830, 7528, 8889, 10000, 11033, 11505, 11623, 11560, 11284};
    private final double PeakPower = 287;//峰值能耗
    private final double PeakPeff = 11623;//峰值效能比
    private final double pp_CPU_util = 0.8;//峰值效能比下的CPU利用率

    @Override
    protected double getPowerData(int index) {
        return power[index];
    }

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

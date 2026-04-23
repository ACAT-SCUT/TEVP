package org.cloudbus.cloudsim.power.models;

public class PowerModelSpecPowerProLiantDL20 extends PowerModelSpecPower {
    /**
     * The power consumption and peff are according to the utilization percentage.
     * power的单位是（W），peff的单位是（ssj_ops）
     */
    private final double[] power = {  21.5	,27.2,30.3,33.1	,36.5,40.7,45.4	,51.1,59.8,70.1,81.5};
    private final double[] peff = {  0, 3104, 5575, 7662, 9240, 10400, 11196, 11586, 11348, 10845, 10430};
    private final double PeakPower = 81.5;//峰值能耗
    private final double PeakPeff = 11586;//峰值效能比
    private final double pp_CPU_util = 0.7;//峰值效能比下的CPU利用率


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

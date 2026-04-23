package org.cloudbus.cloudsim.cooling;

import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ModelInferenceExample {
    public static void main(String[] args) throws IOException, ModelException, TranslateException {
        NDManager manager = NDManager.newBaseManager();
        Criteria<NDList, NDList> criteria = Criteria.builder()
                .setTypes(NDList.class, NDList.class)
                .optModelPath(Paths.get("D://IdeaWorkspace/cloudsim/cloudsim/modules/cloudsim/src/main/modelFiles/ANN.pt"))//1001_0
                .optModelName("ANN_1")
                .build();
        ZooModel model = criteria.loadModel();
        // 输入特征
        float input[] = new float[]{(float)0.8,(float)20, (float) 0.7,21,(float)0.9,(float)21,(float)0.4,(float)24,
                (float)7.8,(float)6.6,(float)7.2,(float)7.8,(float)7.2,
                (float)6.6,(float)7.2,(float)10.2,(float)6.6,(float)7.8,
                (float)9.6,(float)9.6,(float)9.6,(float)11.4,(float)12,
                (float)11.4,(float)9.6,(float)9.0,(float)9.6,(float)9.0};      // 输入的特征属性
        List<NDList> inputData = new LinkedList<>();
        NDArray singleInput = manager.create(input);


        Predictor<NDList, NDList> predictor = model.newPredictor();
        NDList result = predictor.predict(new NDList(singleInput));
        float[] resultArray = result.get(0).toFloatArray();
        ArrayList<Double> HostsInletTempeture= new ArrayList<>(); // 返回主机温度列表
        for (float v : resultArray) {
            HostsInletTempeture.add((double)v);
        }
        System.out.println("Results:"+HostsInletTempeture.get(0));
    }
}

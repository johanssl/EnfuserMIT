/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.interfacing;


/**
 * This interface is intended for advanced, deep learning estimation methods
 * to be used for a number of wide-ranging applications.
 * 
 * A deep learnging, or a Random Forest model is to be setup by storing a training
 * dataset to a specified directory. The training dataset (text file) has a tabled
 * structure and one of the columns is to act as the variable that is estimated
 * based on the other column (features).
 * 
 * This interface defines the protocol for the creation and saving of the models
 * to file. Then, the protocol for model loading is defined. The actual model
 * capabilities are accessed with predict().
 * 
 * Note: The use cases of this interface are still under development
 * and addtional function and methods are likely to be introduced in the future.
 * 
 * @author johanssl
 */
public class Neuralizer {


    
    public Neuralizer() {
        
    }
    
 /**
 * Create, test and save an advanced deep learning model to file using
 * training dataset that are stored in the given directory. While doing this,
 * a test sequence results -file (text) should be also stored to the direction
 * where the variable-to-be predicted is tested against model predictions using
 * a separate evaluation dataset.
 * @param save if true the model is stored as an loadable object to file. 
 * @param dir directory for input and output data
 * @param var variable name to specify the variable of interest, that is,
 * one of the column names in the dataset.
 * @param textMode if true, the model needs to support class-variables, non-numeric
 * features.
 * @param testFrac define the fracion of lines that are to be distributed to
 * "evaluation" rather than training. Default: 0.2.
 */ 
    public void createTestSave(boolean save, String dir, String var,
            boolean textMode, float testFrac) {
    }

 /**
 * Load an existing model from file
 * @param dir directory for data
 * @param name local filename (no path)
 */
    public void loadFromFile(String dir, String name) {

    }

 /**
 * With a given line of features in text, provide a model prediction
 * @param features an array of features as Strings, using the same
 * order and length of variables that was used when the model was created.
 * @return a numeric prediction value based on features.
 */
    public float predict(String[] features) {
        return 0;
    }


    public void loadONC(Object ops) {
       
    }


    public Object getOverride(Object en, Object t, double lat, double lon, int typeInt) {
       return null;
    }


    public Object getOverride(Object en, Object obs) {
        return null;
    }


    public void printoutContent_ONC() {
        
    }


    public boolean hasONC() {
       return false;
    }


    public void clearONC() {
    }

    
    public void createRFModelsForType(Object ops, String sdir, String var, String[] ids) {
      
    }

}

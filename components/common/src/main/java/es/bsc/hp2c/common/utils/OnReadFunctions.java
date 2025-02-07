package es.bsc.hp2c.common.utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Class containing a list of onReadFunctions.
 *
 */
public class OnReadFunctions {
    private ArrayList<OnReadFunction> onReadFuncs;

    public OnReadFunctions(){
        onReadFuncs = new ArrayList<>();
    }

    public void addFunc(OnReadFunction f){
        for(OnReadFunction func : onReadFuncs){
           if (Objects.equals(f.getLabel(), func.getLabel())){
               return;
           }
        }
        onReadFuncs.add(f);
    }

    public ArrayList<OnReadFunction> getOnReadFuncs(){
        return onReadFuncs;
    }
}

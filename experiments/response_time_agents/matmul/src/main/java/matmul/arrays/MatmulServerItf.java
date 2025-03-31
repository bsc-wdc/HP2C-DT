package matmul.arrays;

import es.bsc.compss.types.annotations.Constraints;
import es.bsc.compss.types.annotations.Parameter;
import es.bsc.compss.types.annotations.parameter.Direction;
import es.bsc.compss.types.annotations.task.Method;

public interface MatmulServerItf {

    @Constraints(processorArchitecture = "amd64")
    @Method(declaringClass = "matmul.arrays.MatmulImpl")
    void multiplyAccumulative(
            @Parameter int M
    );

}

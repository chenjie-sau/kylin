/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kylin.storage.hbase.steps;

import org.apache.kylin.common.util.ClassUtil;
import org.apache.kylin.common.util.StringUtil;
import org.apache.kylin.cube.CubeSegment;
import org.apache.kylin.engine.flink.FlinkBatchCubingJobBuilder2;
import org.apache.kylin.engine.flink.FlinkExecutable;
import org.apache.kylin.engine.mr.CubingJob;
import org.apache.kylin.engine.mr.common.AbstractHadoopJob;
import org.apache.kylin.job.constant.ExecutableConstants;
import org.apache.kylin.job.execution.AbstractExecutable;

public class HBaseFlinkSteps extends HBaseJobSteps {

    public HBaseFlinkSteps(CubeSegment seg) {
        super(seg);
    }

    @Override
    public AbstractExecutable createConvertCuboidToHfileStep(String jobId) {
        String cuboidRootPath = getCuboidRootPath(jobId);
        String inputPath = cuboidRootPath + (cuboidRootPath.endsWith("/") ? "" : "/");

        FlinkBatchCubingJobBuilder2 jobBuilder2 = new FlinkBatchCubingJobBuilder2(seg, null, 0);
        final FlinkExecutable flinkExecutable = new FlinkExecutable();
        flinkExecutable.setClassName(FlinkCubeHFile.class.getName());
        flinkExecutable.setParam(FlinkCubeHFile.OPTION_CUBE_NAME.getOpt(), seg.getRealization().getName());
        flinkExecutable.setParam(FlinkCubeHFile.OPTION_SEGMENT_ID.getOpt(), seg.getUuid());
        flinkExecutable.setParam(FlinkCubeHFile.OPTION_META_URL.getOpt(),
                jobBuilder2.getSegmentMetadataUrl(seg.getConfig(), jobId));
        flinkExecutable.setParam(FlinkCubeHFile.OPTION_OUTPUT_PATH.getOpt(), getHFilePath(jobId));
        flinkExecutable.setParam(FlinkCubeHFile.OPTION_INPUT_PATH.getOpt(), inputPath);
        flinkExecutable.setParam(FlinkCubeHFile.OPTION_PARTITION_FILE_PATH.getOpt(),
                getRowkeyDistributionOutputPath(jobId) + "/part-r-00000_hfile");
        flinkExecutable.setParam(AbstractHadoopJob.OPTION_HBASE_CONF_PATH.getOpt(), getHBaseConfFilePath(jobId));
        flinkExecutable.setJobId(jobId);

        String[] filterJar = seg.getConfig().getSparkConvertCuboidDataJarsFilter().split(",");
        StringBuilder jars = new StringBuilder();
        StringUtil.appendWithSeparator(jars, ClassUtil.findContainingJar(filterJar, org.apache.hadoop.hbase.KeyValue.class));
        StringUtil.appendWithSeparator(jars,
                ClassUtil.findContainingJar(filterJar, org.apache.hadoop.hbase.mapreduce.HFileOutputFormat2.class));
        StringUtil.appendWithSeparator(jars,
                ClassUtil.findContainingJar(filterJar, org.apache.hadoop.hbase.regionserver.BloomType.class));
        StringUtil.appendWithSeparator(jars,
                ClassUtil.findContainingJar(filterJar, org.apache.hadoop.hbase.protobuf.generated.HFileProtos.class)); //hbase-protocal.jar
        StringUtil.appendWithSeparator(jars,
                ClassUtil.findContainingJar(filterJar, org.apache.hadoop.hbase.CompatibilityFactory.class)); //hbase-hadoop-compact.jar
        StringUtil.appendWithSeparator(jars, ClassUtil.findContainingJar(filterJar, "org.htrace.HTraceConfiguration", null)); // htrace-core.jar
        StringUtil.appendWithSeparator(jars, ClassUtil.findContainingJar(filterJar, "org.apache.htrace.Trace", null)); // htrace-core.jar
        StringUtil.appendWithSeparator(jars,
                ClassUtil.findContainingJar(filterJar, "com.yammer.metrics.core.MetricsRegistry", null)); // metrics-core.jar
        //KYLIN-3607
        StringUtil.appendWithSeparator(jars,
                ClassUtil.findContainingJar(filterJar, "org.apache.hadoop.hbase.regionserver.MetricsRegionServerSourceFactory", null));//hbase-hadoop-compat-1.1.1.jar
        StringUtil.appendWithSeparator(jars,
                ClassUtil.findContainingJar(filterJar, "org.apache.hadoop.hbase.regionserver.MetricsRegionServerSourceFactoryImpl", null));//hbase-hadoop2-compat-1.1.1.jar

        //KYLIN-3537
        StringUtil.appendWithSeparator(jars,
                ClassUtil.findContainingJar(filterJar, "org.apache.hadoop.hbase.io.hfile.HFileWriterImpl", null));//hbase-server.jar
        StringUtil.appendWithSeparator(jars,
                ClassUtil.findContainingJar(filterJar, "org.apache.hbase.thirdparty.com.google.common.cache.CacheLoader", null));//hbase-shaded-miscellaneous.jar
        StringUtil.appendWithSeparator(jars,
                ClassUtil.findContainingJar(filterJar, "org.apache.hadoop.hbase.metrics.MetricRegistry", null));//hbase-metrics-api.jar
        StringUtil.appendWithSeparator(jars,
                ClassUtil.findContainingJar(filterJar, "org.apache.hadoop.hbase.metrics.impl.MetricRegistriesImpl", null));//hbase-metrics.jar
        StringUtil.appendWithSeparator(jars,
                ClassUtil.findContainingJar(filterJar, "org.apache.hbase.thirdparty.com.google.protobuf.Message", null));//hbase-shaded-protobuf.jar
        StringUtil.appendWithSeparator(jars,
                ClassUtil.findContainingJar(filterJar, "org.apache.hadoop.hbase.shaded.protobuf.generated.HFileProtos", null));//hbase-protocol-shaded.jar

        if (!StringUtil.isEmpty(seg.getConfig().getFlinkAdditionalJars())) {
            StringUtil.appendWithSeparator(jars, seg.getConfig().getFlinkAdditionalJars());
        }
        flinkExecutable.setJars(jars.toString());

        flinkExecutable.setName(ExecutableConstants.STEP_NAME_CONVERT_CUBOID_TO_HFILE);
        flinkExecutable.setCounterSaveAs(",," + CubingJob.CUBE_SIZE_BYTES, getCounterOutputPath(jobId));

        return flinkExecutable;
    }
}

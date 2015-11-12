/*
 * Copyright 2015 National Bank of Belgium
 * 
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved 
 * by the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * 
 * http://ec.europa.eu/idabc/eupl
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and 
 * limitations under the Licence.
 */
package demetra.cli.anomalydetection;

import com.google.common.annotations.VisibleForTesting;
import demetra.cli.helpers.StandardApp;
import demetra.cli.helpers.BasicArgsParser;
import demetra.cli.helpers.InputOptions;
import demetra.cli.helpers.OptionsSpec;
import static demetra.cli.helpers.OptionsSpec.newInputOptionsSpec;
import static demetra.cli.helpers.OptionsSpec.newOutputOptionsSpec;
import static demetra.cli.helpers.OptionsSpec.newStandardOptionsSpec;
import demetra.cli.helpers.OutputOptions;
import demetra.cli.helpers.StandardOptions;
import ec.tss.TsCollectionInformation;
import ec.tss.xml.XmlTsCollection;
import ec.tstoolkit.modelling.DefaultTransformationType;
import ec.tstoolkit.timeseries.regression.OutlierEstimation;
import ec.tstoolkit.timeseries.regression.OutlierType;
import static ec.tstoolkit.timeseries.regression.OutlierType.AO;
import static ec.tstoolkit.timeseries.regression.OutlierType.LS;
import static ec.tstoolkit.timeseries.regression.OutlierType.SO;
import static ec.tstoolkit.timeseries.regression.OutlierType.TC;
import static java.util.Arrays.asList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import lombok.Value;

/**
 *
 * @author Philippe Charles
 */
public final class Ts2Outliers extends StandardApp<Ts2Outliers.Parameters> {

    public static void main(String[] args) {
        new Ts2Outliers().run(args, new Parser());
    }

    @Value
    public static class Parameters {

        StandardOptions so;
        InputOptions input;
        OutliersOptions spec;
        OutputOptions output;
    }

    @Override
    public void exec(Parameters params) throws Exception {
        TsCollectionInformation input = params.input.readValue(XmlTsCollection.class);

        if (params.so.isVerbose()) {
            summarize(input);
        }

        OutliersFactory.Callback callback = params.so.isVerbose() ? new PrintPercent(input.items.size()) : new OutliersFactory.Callback();
        List<OutlierEstimation[]> data = params.spec.newOutliersFactory().process(input, callback);

        XmlOutliersTsCollection output = XmlOutliersTsCollection.create(input, data);

        params.output.write(XmlOutliersTsCollection.class, output);
    }

    @Override
    protected StandardOptions getStandardOptions(Parameters params) {
        return params.so;
    }

    private static void summarize(TsCollectionInformation info) {
        System.err.println("Processing " + info.items.size() + " time series");
    }

    @VisibleForTesting
    static final class Parser extends BasicArgsParser<Parameters> {

        private final OptionsSpec<StandardOptions> so = newStandardOptionsSpec(parser);
        private final OptionsSpec<InputOptions> input = newInputOptionsSpec(parser);
        private final OptionsSpec<OutliersOptions> spec = new OutliersOptionsSpec(parser);
        private final OptionsSpec<OutputOptions> output = newOutputOptionsSpec(parser);

        @Override
        protected Parameters parse(OptionSet options) {
            return new Parameters(so.value(options), input.value(options), spec.value(options), output.value(options));
        }
    }

    private static final class OutliersOptionsSpec extends OptionsSpec<OutliersOptions> {

        private final OptionSpec<DefaultSpec> defaultSpec;
        private final OptionSpec<Double> critVal;
        private final OptionSpec<DefaultTransformationType> transformation;
        private final OptionSpec<OutlierType> outlierTypes;

        public OutliersOptionsSpec(OptionParser parser) {
            this.defaultSpec = parser
                    .acceptsAll(asList("s", "default-spec"), "Default spec " + BasicArgsParser.toString(DefaultSpec.values()))
                    .withRequiredArg()
                    .ofType(DefaultSpec.class)
                    .defaultsTo(DefaultSpec.TR4);
            this.critVal = parser
                    .acceptsAll(asList("c", "critical-value"), "Critical value")
                    .withRequiredArg()
                    .ofType(Double.class)
                    .defaultsTo(0d);
            this.transformation = parser
                    .acceptsAll(asList("t", "transformation"), "Transformation " + BasicArgsParser.toString(DefaultTransformationType.values()))
                    .withRequiredArg()
                    .ofType(DefaultTransformationType.class)
                    .defaultsTo(DefaultTransformationType.None);
            this.outlierTypes = parser
                    .acceptsAll(asList("x", "outlier-types"), "Comma-separated list of outlier types " + BasicArgsParser.toString(AO, LS, TC, SO))
                    .withRequiredArg()
                    .ofType(OutlierType.class)
                    .withValuesSeparatedBy(',')
                    .defaultsTo(AO, LS, TC);
        }

        @Override
        public OutliersOptions value(OptionSet options) {
            return new OutliersOptions(defaultSpec.value(options), critVal.value(options), transformation.value(options), EnumSet.copyOf(outlierTypes.values(options)));
        }
    }

    private static final class PrintPercent extends OutliersFactory.Callback {

        final int size;
        final AtomicInteger cpt = new AtomicInteger(0);
        final AtomicInteger previous = new AtomicInteger(0);

        public PrintPercent(int size) {
            this.size = size;
        }

        @Override
        public void publish(int index, OutlierEstimation[][] outliers) {
            int percent = 100 * cpt.addAndGet(outliers.length) / size;
            int old = previous.getAndSet(percent);
            if (old != percent) {
                System.err.println("Processed: " + percent + "%");
            }
        }
    }
}
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
package demetra.cli.tsproviders;

import com.google.common.annotations.VisibleForTesting;
import demetra.cli.helpers.BasicArgsParser;
import com.google.common.base.Optional;
import demetra.cli.helpers.StandardApp;
import demetra.cli.helpers.InputOptions;
import demetra.cli.helpers.OptionsSpec;
import static demetra.cli.helpers.OptionsSpec.newInputOptionsSpec;
import static demetra.cli.helpers.OptionsSpec.newStandardOptionsSpec;
import demetra.cli.helpers.StandardOptions;
import ec.tss.TsCollectionInformation;
import ec.tss.tsproviders.spreadsheet.engine.SpreadSheetFactory;
import ec.tss.tsproviders.spreadsheet.engine.TsExportOptions;
import ec.tss.xml.XmlTsCollection;
import ec.util.spreadsheet.Book;
import ec.util.spreadsheet.helpers.ArraySheet;
import java.io.File;
import java.util.ServiceLoader;
import javax.xml.bind.annotation.XmlRootElement;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

/**
 *
 * @author Philippe Charles
 */
public final class Ts2SpreadSheet extends StandardApp<Ts2SpreadSheet.Parameters> {

    public static void main(String[] args) {
        new Ts2SpreadSheet().run(args, new Parser());
    }

    @XmlRootElement
    public static final class Parameters {

        StandardOptions so;
        public InputOptions input;
        public File outputFile;
        public TsExportOptions exportOptions;
    }

    @Override
    public void exec(Parameters params) throws Exception {
        TsCollectionInformation info = params.input.readValue(XmlTsCollection.class);
        Optional<Book.Factory> factory = getFactory(params.outputFile);
        if (factory.isPresent()) {
            ArraySheet sheet = SpreadSheetFactory.getDefault().fromTsCollectionInfo(info, params.exportOptions);
            factory.get().store(params.outputFile, sheet.toBook());
        } else {
            throw new IllegalArgumentException("Cannot handle file '" + params.outputFile.toString() + "'");
        }
    }

    @Override
    protected StandardOptions getStandardOptions(Parameters params) {
        return params.so;
    }

    private Optional<Book.Factory> getFactory(File file) {
        for (Book.Factory o : ServiceLoader.load(Book.Factory.class)) {
            if (o.canStore() && o.accept(file)) {
                return Optional.of(o);
            }
        }
        return Optional.absent();
    }

    @VisibleForTesting
    static final class Parser extends BasicArgsParser<Parameters> {

        private final OptionsSpec<StandardOptions> so = newStandardOptionsSpec(parser);
        private final OptionsSpec<InputOptions> input = newInputOptionsSpec(parser);
        private final OptionSpec<File> outputFile = parser.nonOptions("Output file").ofType(File.class);
        private final OptionsSpec<TsExportOptions> exportOptions = new TsExportOptionsSpec(parser);

        @Override
        protected Parameters parse(OptionSet options) {
            Parameters result = new Parameters();
            result.input = input.value(options);
            result.outputFile = options.has(outputFile) ? outputFile.value(options) : null;
            result.exportOptions = exportOptions.value(options);
            result.so = so.value(options);
            return result;
        }
    }

    private static final class TsExportOptionsSpec extends OptionsSpec<TsExportOptions> {

        private final OptionSpec<Void> horizontal;
        private final OptionSpec<Void> hideDates;
        private final OptionSpec<Void> hideNames;
        private final OptionSpec<Void> endPeriod;

        public TsExportOptionsSpec(OptionParser parser) {
            this.horizontal = parser.accepts("horizontal");
            this.hideDates = parser.accepts("hideDates");
            this.hideNames = parser.accepts("hideNames");
            this.endPeriod = parser.accepts("endPeriod");
        }

        @Override
        public TsExportOptions value(OptionSet options) {
            return TsExportOptions.create(!options.has(horizontal), !options.has(hideDates), !options.has(hideNames), !options.has(endPeriod));
        }
    }
}
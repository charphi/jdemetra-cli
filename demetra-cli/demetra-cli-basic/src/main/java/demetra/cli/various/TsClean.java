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
package demetra.cli.various;

import be.nbb.cli.command.Command;
import be.nbb.cli.command.core.OptionsExecutor;
import be.nbb.cli.command.core.OptionsParsingCommand;
import be.nbb.cli.command.joptsimple.ComposedOptionSpec;
import static be.nbb.cli.command.joptsimple.ComposedOptionSpec.newInputOptionsSpec;
import static be.nbb.cli.command.joptsimple.ComposedOptionSpec.newOutputOptionsSpec;
import static be.nbb.cli.command.joptsimple.ComposedOptionSpec.newStandardOptionsSpec;
import be.nbb.cli.command.joptsimple.JOptSimpleParser;
import be.nbb.cli.command.proc.CommandRegistration;
import be.nbb.cli.util.InputOptions;
import be.nbb.cli.util.OutputOptions;
import be.nbb.cli.util.StandardOptions;
import com.google.common.base.Joiner;
import demetra.cli.helpers.XmlUtil;
import ec.tss.TsCollectionInformation;
import ec.tss.TsInformationType;
import ec.tss.TsMoniker;
import ec.tss.xml.XmlTsCollection;
import ec.tstoolkit.design.VisibleForTesting;
import java.util.EnumSet;
import java.util.Set;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.openide.util.NbBundle;

/**
 *
 * @author Philippe Charles
 */
@lombok.experimental.UtilityClass
public final class TsClean {

    @CommandRegistration(name = "tsclean")
    static final Command CMD = OptionsParsingCommand.of(Parser::new, Executor::new, o -> o.so);

    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static final class Options {

        StandardOptions so;
        public InputOptions input;
        public EnumSet<TsItem> itemsToRemove;
        public OutputOptions output;
    }

    @VisibleForTesting
    static final class Executor implements OptionsExecutor<Options> {

        @Override
        public void exec(Options o) throws Exception {
            TsCollectionInformation result = XmlUtil.readValue(o.input, XmlTsCollection.class);
            if (!o.itemsToRemove.isEmpty()) {
                removeItems(result, o.itemsToRemove);
            }
            XmlUtil.writeValue(o.output, XmlTsCollection.class, result);
        }

        @VisibleForTesting
        static void removeItems(TsCollectionInformation info, Set<TsItem> items) {
            info.name = items.contains(TsItem.name) ? null : info.name;
            info.moniker = items.contains(TsItem.moniker) ? new TsMoniker() : info.moniker;
            info.metaData = items.contains(TsItem.metaData) ? null : info.metaData;
            info.invalidDataCause = items.contains(TsItem.cause) ? null : info.invalidDataCause;
            info.type = items.contains(TsItem.type) ? TsInformationType.UserDefined : info.type;
            info.items.forEach(o -> {
                o.name = items.contains(TsItem.name) ? null : o.name;
                o.moniker = items.contains(TsItem.moniker) ? new TsMoniker() : o.moniker;
                o.metaData = o.hasMetaData() ? (items.contains(TsItem.metaData) ? null : o.metaData) : null;
                o.invalidDataCause = items.contains(TsItem.cause) ? null : o.invalidDataCause;
                o.type = items.contains(TsItem.type) ? TsInformationType.UserDefined : o.type;
                o.data = o.hasData() ? (items.contains(TsItem.data) ? null : o.data) : null;
            });
        }
    }

    @VisibleForTesting
    static final class Parser extends JOptSimpleParser<Options> {

        private final ComposedOptionSpec<StandardOptions> so = newStandardOptionsSpec(parser);
        private final ComposedOptionSpec<InputOptions> input = newInputOptionsSpec(parser);
        private final ComposedOptionSpec<EnumSet<TsItem>> itemsToRemove = new ItemsToRemoveSpec(parser);
        private final ComposedOptionSpec<OutputOptions> output = newOutputOptionsSpec(parser);

        @Override
        protected Options parse(OptionSet o) {
            return new Options(so.value(o), input.value(o), itemsToRemove.value(o), output.value(o));
        }
    }

    @NbBundle.Messages({
        "# {0} - items to remove",
        "tsfilter.items=Comma-separated list of items to remove [{0}]"
    })
    private static final class ItemsToRemoveSpec implements ComposedOptionSpec<EnumSet<TsItem>> {

        private final OptionSpec<TsItem> itemsToRemove;

        public ItemsToRemoveSpec(OptionParser p) {
            Joiner joiner = Joiner.on(", ");
            this.itemsToRemove = p
                    .accepts("remove", Bundle.tsfilter_items(joiner.join(TsItem.values())))
                    .withRequiredArg()
                    .ofType(TsItem.class)
                    .withValuesSeparatedBy(',');
        }

        @Override
        public EnumSet<TsItem> value(OptionSet o) {
            return o.has(itemsToRemove) ? EnumSet.copyOf(itemsToRemove.values(o)) : EnumSet.noneOf(TsItem.class);
        }
    }
}

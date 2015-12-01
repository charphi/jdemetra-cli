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
package demetra.cli.chart;

import com.google.common.base.Preconditions;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableList;
import com.google.common.net.MediaType;
import ec.tss.Ts;
import ec.tss.TsCollectionInformation;
import ec.tss.TsInformation;
import ec.tstoolkit.design.IBuilder;
import ec.tstoolkit.design.NewObject;
import ec.tstoolkit.timeseries.simplets.TsData;
import ec.tstoolkit.timeseries.simplets.TsPeriod;
import ec.util.chart.ColorScheme;
import ec.util.chart.ObsFunction;
import ec.util.chart.SeriesFunction;
import ec.util.chart.impl.SmartColorScheme;
import ec.util.chart.swing.Charts;
import ec.util.chart.swing.JTimeSeriesChart;
import ec.util.chart.swing.SwingColorSchemeSupport;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.Nonnull;
import org.jfree.data.DomainOrder;
import org.jfree.data.general.AbstractSeriesDataset;
import org.jfree.data.xy.IntervalXYDataset;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = ChartTool.class)
public final class ChartToolImpl implements ChartTool {

    @Override
    public void writeChart(TsCollectionInformation col, Options options, OutputStream stream, MediaType mediaType) throws IOException {
        JTimeSeriesChart chart = new JTimeSeriesChart();
        applyContent(chart, col);
        applyOptions(chart, options);
        chart.doLayout();
        chart.writeImage(mediaType.withoutParameters().toString(), stream);
    }

    //<editor-fold defaultstate="collapsed" desc="Implementation details">
    private void applyContent(JTimeSeriesChart chart, TsCollectionInformation info) {
        chart.setTitle(info.name);
        chart.setDataset(getDataset(info));
        chart.setSeriesFormatter(getSeriesFormatter(info));
        chart.setObsFormatter(getObsFormatter(info));
    }

    private void applyOptions(JTimeSeriesChart chart, Options options) {
        chart.setSize(options.getWidth(), options.getHeight());
        chart.setColorSchemeSupport(SwingColorSchemeSupport.from(getColorScheme(options.getColorScheme())));
        if (!options.getTitle().isEmpty()) {
            chart.setTitle(options.getTitle());
        }
    }

    private ObsFunction<String> getObsFormatter(final TsCollectionInformation info) {
        return new ObsFunction<String>() {
            @Override
            public String apply(int series, int obs) {
                TsData data = info.items.get(series).data;
                return data.getDomain().get(obs) + " : " + data.get(obs);
            }
        };
    }

    private SeriesFunction<String> getSeriesFormatter(final TsCollectionInformation info) {
        return new SeriesFunction<String>() {
            @Override
            public String apply(int series) {
                return info.items.get(series).name;
            }
        };
    }

    private ColorScheme getColorScheme(String name) {
        for (ColorScheme o : ServiceLoader.load(ColorScheme.class)) {
            if (o.getName().equals(name)) {
                return o;
            }
        }
        return new SmartColorScheme();
    }

    private IntervalXYDataset getDataset(TsCollectionInformation info) {
        TsXYDatasets.Builder result = TsXYDatasets.builder();
        info.items.stream().filter(TsInformation::hasData).forEach((o) -> {
            result.add(o.name, o.data);
        });
        return result.build();
    }

    // copy from nbdemetra-ui
    private static final class TsXYDatasets {

        private TsXYDatasets() {
            // static class
        }

        @Nonnull
        @NewObject
        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder implements IBuilder<IntervalXYDataset> {

            // Let's avoid duplicated keys.
            private final Set<Comparable<?>> keys = new HashSet<>();
            private final List<TsFacade> list = new ArrayList<>();

            private void checkKey(Comparable<?> key) {
                Preconditions.checkNotNull(key, "Cannot add null key");
                Preconditions.checkArgument(!keys.contains(key), "Duplicated key");
            }

            private void add(TsFacade item) {
                keys.add(item.getKey());
                list.add(item);
            }

            @Nonnull
            public Builder add(@Nonnull Comparable<?> key, @Nonnull TsData data) {
                checkKey(key);
                checkNotNull(data, "Data cannot be null");
                if (!data.isEmpty()) {
                    add(FastTs.create(key, data));
                }
                return this;
            }

            @Nonnull
            public Builder clear() {
                keys.clear();
                list.clear();
                return this;
            }

            @Override
            public IntervalXYDataset build() {
                switch (list.size()) {
                    case 0:
                        return Charts.emptyXYDataset();
                    case 1:
                        return new SingleTsXYDataset(list.get(0));
                    default:
                        return new MultiTsXYDataset(ImmutableList.copyOf(list));
                }
            }
        }

        //<editor-fold defaultstate="collapsed" desc="Internal implementation">
        private static abstract class AbstractTsXYDataset extends AbstractSeriesDataset implements IntervalXYDataset {

            @Override
            public Number getStartX(int series, int item) {
                return getStartXValue(series, item);
            }

            @Override
            public Number getEndX(int series, int item) {
                return getEndXValue(series, item);
            }

            @Override
            public Number getStartY(int series, int item) {
                return getEndYValue(series, item);
            }

            @Override
            public double getStartYValue(int series, int item) {
                return getYValue(series, item);
            }

            @Override
            public Number getEndY(int series, int item) {
                return getEndYValue(series, item);
            }

            @Override
            public double getEndYValue(int series, int item) {
                return getYValue(series, item);
            }

            @Override
            public DomainOrder getDomainOrder() {
                return DomainOrder.NONE;
            }

            @Override
            public Number getX(int series, int item) {
                return getXValue(series, item);
            }

            @Override
            public Number getY(int series, int item) {
                return getYValue(series, item);
            }
        }

        private static final class MultiTsXYDataset extends AbstractTsXYDataset {

            private final List<TsFacade> list;

            private MultiTsXYDataset(@Nonnull List<TsFacade> list) {
                this.list = list;
            }

            @Override
            public int getSeriesCount() {
                return list.size();
            }

            @Override
            public Comparable<?> getSeriesKey(int series) {
                return list.get(series).getKey();
            }

            @Override
            public double getStartXValue(int series, int item) {
                return list.get(series).getStartTimeInMillis(item);
            }

            @Override
            public double getEndXValue(int series, int item) {
                return list.get(series).getEndTimeInMillis(item);
            }

            @Override
            public int getItemCount(int series) {
                return list.get(series).getItemCount();
            }

            @Override
            public double getXValue(int series, int item) {
                return list.get(series).getMiddleTimeInMillis(item);
            }

            @Override
            public double getYValue(int series, int item) {
                return list.get(series).getValue(item);
            }
        }

        private static final class SingleTsXYDataset extends AbstractTsXYDataset {

            private final TsFacade singleton;

            private SingleTsXYDataset(@Nonnull TsFacade singleton) {
                this.singleton = singleton;
            }

            @Override
            public int getSeriesCount() {
                return 1;
            }

            @Override
            public Comparable<?> getSeriesKey(int series) {
                return singleton.getKey();
            }

            @Override
            public double getStartXValue(int series, int item) {
                return singleton.getStartTimeInMillis(item);
            }

            @Override
            public double getEndXValue(int series, int item) {
                return singleton.getEndTimeInMillis(item);
            }

            @Override
            public int getItemCount(int series) {
                return singleton.getItemCount();
            }

            @Override
            public double getXValue(int series, int item) {
                return singleton.getMiddleTimeInMillis(item);
            }

            @Override
            public double getYValue(int series, int item) {
                return singleton.getValue(item);
            }
        }

        private interface TsFacade {

            Comparable<?> getKey();

            int getItemCount();

            double getValue(int item);

            long getStartTimeInMillis(int item);

            long getEndTimeInMillis(int item);

            long getMiddleTimeInMillis(int item);

        }

        /**
         * A simple but efficient structure that holds every essential TS
         * information and data.
         */
        private static final class FastTs implements TsFacade {

            // a shared calendar used in the event dispatch thread
            private static final Calendar EDT_CALENDAR = Calendar.getInstance();

            // FACTORY METHODS
            @Nonnull
            static FastTs create(@Nonnull Ts ts) {
                return create(ts.getMoniker(), ts.getTsData());
            }

            @Nonnull
            static FastTs create(@Nonnull Comparable<?> key, @Nonnull TsData data) {
                return create(key, data.getStart(), data.getValues().internalStorage());
            }

            @Nonnull
            static FastTs create(@Nonnull Comparable<?> key, @Nonnull TsPeriod start, @Nonnull double[] data) {
                int freq = start.getFrequency().intValue();
                int id = start.hashCode(); // quick&dirty hack
                return (id >= 0)
                        ? new FastTs(EDT_CALENDAR, key, freq, (1970 + id / freq), (id % freq), data)
                        : new FastTs(EDT_CALENDAR, key, freq, (1969 + (1 + id) / freq), (freq - 1 + (1 + id) % freq), data);
            }
            // PROPERTIES
            private final Calendar cal;
            private final Comparable<?> key;
            private final int freq;
            private final int startYear;
            private final int startPos;
            private final double[] data;

            private FastTs(@Nonnull Calendar cal, @Nonnull Comparable<?> key, int freq, int startYear, int startPos, @Nonnull double[] data) {
                this.cal = cal;
                this.key = key;
                this.freq = freq;
                this.startYear = startYear;
                this.startPos = startPos;
                this.data = data;
            }

            @Override
            public Comparable<?> getKey() {
                return key;
            }

            @Override
            public int getItemCount() {
                return data.length;
            }

            @Override
            public double getValue(int item) {
                return data[item];
            }

            @Override
            public long getStartTimeInMillis(int item) {
                final int tmp = startPos + item;
                cal.set(Calendar.YEAR, startYear + tmp / freq);
                cal.set(Calendar.MONTH, (tmp % freq) * (12 / freq));
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                return cal.getTimeInMillis();
            }

            @Override
            public long getEndTimeInMillis(int item) {
                return getStartTimeInMillis(item + 1);
            }

            @Override
            public long getMiddleTimeInMillis(int item) {
                long start = getStartTimeInMillis(item);
                cal.add(Calendar.MONTH, 12 / freq);
                long end = cal.getTimeInMillis();
                return start + (end - start) / 2;
            }
        }
        //</editor-fold>
    }
    //</editor-fold>
}

import React, { useId, useState } from "react";
import { Dimensions, StyleSheet, Text, View } from "react-native";
import Svg, { Circle, Defs, Line, LinearGradient, Path, Stop, Text as SvgText } from "react-native-svg";
import { useColors } from "@/hooks/useColors";
import { usePreferences } from "@/context/PreferencesContext";

export interface DataPoint {
  label: string; // x-axis label (e.g. date)
  value: number;
}

interface Props {
  data: DataPoint[];
  color?: string;
  unit?: string;
  height?: number;
  formatValue?: (v: number) => string;
  goalLine?: number;
}

const PADDING_LEFT = 44;
const PADDING_RIGHT = 16;
const PADDING_TOP = 16;
const PADDING_BOTTOM = 36;

export default function LineChart({ data, color, unit = "", height = 180, formatValue, goalLine }: Props) {
  const colors = useColors();
  const { t } = usePreferences();
  // Unique per chart: several charts share a screen and SVG ids are global.
  const gradientId = `fillGrad-${useId().replace(/:/g, "")}`;
  const chartColor = color ?? colors.primary;
  // Fit the parent's width (measured); estimate from the screen until the first layout.
  const [measuredW, setMeasuredW] = useState<number | null>(null);
  const W = measuredW ?? Dimensions.get("window").width - 72;
  const H = height;

  const innerW = W - PADDING_LEFT - PADDING_RIGHT;
  const innerH = H - PADDING_TOP - PADDING_BOTTOM;

  if (data.length < 2) {
    return (
      <View style={[styles.empty, { height: H }]}>
        <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
          {data.length === 0 ? t("chart.noData") : t("chart.onePoint")}
        </Text>
      </View>
    );
  }

  const values = data.map((d) => d.value);
  const maxVal = Math.max(...values, ...(goalLine != null ? [goalLine] : []));
  const minVal = Math.min(...values, ...(goalLine != null ? [goalLine] : []));
  const range = maxVal - minVal || 1;

  // Map data to SVG coordinates
  function getX(i: number) {
    return PADDING_LEFT + (i / (data.length - 1)) * innerW;
  }
  function getY(val: number) {
    return PADDING_TOP + innerH - ((val - minVal) / range) * innerH;
  }

  // Build smooth polyline path using cubic bezier
  function buildPath(): string {
    if (data.length < 2) return "";
    let d = `M ${getX(0)} ${getY(data[0].value)}`;
    for (let i = 1; i < data.length; i++) {
      const x0 = getX(i - 1);
      const y0 = getY(data[i - 1].value);
      const x1 = getX(i);
      const y1 = getY(data[i].value);
      const cpx = (x0 + x1) / 2;
      d += ` C ${cpx} ${y0}, ${cpx} ${y1}, ${x1} ${y1}`;
    }
    return d;
  }

  // Fill path (closed below)
  function buildFill(): string {
    const linePath = buildPath();
    const lastX = getX(data.length - 1);
    const baseY = PADDING_TOP + innerH;
    return `${linePath} L ${lastX} ${baseY} L ${PADDING_LEFT} ${baseY} Z`;
  }

  const fmt = formatValue ?? ((v: number) => `${Math.round(v)}`);

  // Y-axis ticks (3 ticks)
  const yTicks = [minVal, (minVal + maxVal) / 2, maxVal];

  // X-axis: show max 5 labels evenly spaced
  const xStep = Math.max(1, Math.floor(data.length / 5));
  const xLabels = data
    .map((d, i) => ({ label: d.label, i }))
    .filter((_, idx) => idx === 0 || idx === data.length - 1 || idx % xStep === 0);

  // Find the highest point index for the tooltip
  const peakIdx = values.indexOf(maxVal);

  return (
    <View onLayout={(e) => setMeasuredW(e.nativeEvent.layout.width)}>
      <Svg width={W} height={H}>
        <Defs>
          <LinearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
            <Stop offset="0%" stopColor={chartColor} stopOpacity="0.25" />
            <Stop offset="100%" stopColor={chartColor} stopOpacity="0.02" />
          </LinearGradient>
        </Defs>

        {/* Horizontal grid lines */}
        {yTicks.map((tick, i) => (
          <Line
            key={i}
            x1={PADDING_LEFT}
            y1={getY(tick)}
            x2={W - PADDING_RIGHT}
            y2={getY(tick)}
            stroke={colors.border}
            strokeWidth={1}
            strokeDasharray="4 4"
          />
        ))}

        {/* Y-axis labels */}
        {yTicks.map((tick, i) => (
          <SvgText
            key={i}
            x={PADDING_LEFT - 6}
            y={getY(tick) + 4}
            fontSize={10}
            fill={colors.mutedForeground}
            textAnchor="end"
            fontFamily="Inter_400Regular"
          >
            {fmt(tick)}
          </SvgText>
        ))}

        {/* Goal line */}
        {goalLine != null && (
          <>
            <Line
              x1={PADDING_LEFT}
              y1={getY(goalLine)}
              x2={W - PADDING_RIGHT}
              y2={getY(goalLine)}
              stroke={chartColor}
              strokeWidth={1.5}
              strokeDasharray="6 4"
              strokeOpacity={0.5}
            />
            <SvgText
              x={W - PADDING_RIGHT}
              y={getY(goalLine) - 4}
              fontSize={9}
              fill={chartColor}
              textAnchor="end"
              fontFamily="Inter_600SemiBold"
              fillOpacity={0.7}
            >
              {t("chart.goal")}
            </SvgText>
          </>
        )}

        {/* Fill area */}
        <Path d={buildFill()} fill={`url(#${gradientId})`} />

        {/* Line */}
        <Path
          d={buildPath()}
          stroke={chartColor}
          strokeWidth={2.5}
          fill="none"
          strokeLinecap="round"
          strokeLinejoin="round"
        />

        {/* Data points */}
        {data.map((d, i) => {
          const isPeak = i === peakIdx;
          return (
            <Circle
              key={i}
              cx={getX(i)}
              cy={getY(d.value)}
              r={isPeak ? 5 : 3.5}
              fill={isPeak ? chartColor : colors.background}
              stroke={chartColor}
              strokeWidth={isPeak ? 0 : 2}
            />
          );
        })}

        {/* Peak value tooltip */}
        {data.length > 1 && (
          <>
            <SvgText
              x={getX(peakIdx)}
              y={getY(maxVal) - 10}
              fontSize={11}
              fill={chartColor}
              textAnchor="middle"
              fontFamily="Inter_700Bold"
            >
              {fmt(maxVal)}{unit}
            </SvgText>
          </>
        )}

        {/* X-axis labels */}
        {xLabels.map(({ label, i }) => (
          <SvgText
            key={i}
            x={getX(i)}
            y={H - 6}
            fontSize={9.5}
            fill={colors.mutedForeground}
            textAnchor="middle"
            fontFamily="Inter_400Regular"
          >
            {label}
          </SvgText>
        ))}
      </Svg>
    </View>
  );
}

const styles = StyleSheet.create({
  empty: {
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 24,
  },
  emptyText: {
    fontSize: 13,
    fontFamily: "Inter_400Regular",
    textAlign: "center",
  },
});

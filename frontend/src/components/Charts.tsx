export function CountChart({
  title,
  data,
}: {
  title: string;
  data: { label: string; value: number }[];
}) {
  const max = Math.max(4, Math.ceil(Math.max(...data.map((d) => d.value), 0) / 4) * 4);
  const height = data.length * 42 + 35;
  const width = 520,
    left = 110,
    right = 40,
    plot = width - left - right;
  return (
    <div className="count-chart">
      <svg
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label={`${title}: ${data.map((d) => `${d.label} ${d.value}`).join(', ')}`}
      >
        {Array.from({ length: 5 }, (_, i) => (
          <g key={i}>
            <line
              x1={left + (plot * i) / 4}
              x2={left + (plot * i) / 4}
              y1="0"
              y2={height - 25}
              className="chart-grid"
            />
            <text
              x={left + (plot * i) / 4}
              y={height - 7}
              textAnchor="middle"
              className="chart-label"
            >
              {(max * i) / 4}
            </text>
          </g>
        ))}
        {data.map((d, i) => (
          <g key={d.label}>
            <text x={left - 12} y={i * 42 + 23} textAnchor="end" className="chart-label">
              {d.label}
            </text>
            <rect
              x={left}
              y={i * 42 + 7}
              width={(plot * d.value) / max}
              height="23"
              rx="2"
              className="chart-bar"
            />
            <text
              x={Math.min(left + (plot * d.value) / max + 9, width - 20)}
              y={i * 42 + 23}
              className="chart-value"
            >
              {d.value}
            </text>
          </g>
        ))}
      </svg>
    </div>
  );
}

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: { teal: "#00CDAC", tealDark: "#00A98E", navy: "#0E1133" },
      },
      boxShadow: {
        // Elevation tint pulled from brand.navy instead of neutral black, so
        // depth reads as designed rather than a generic drop shadow.
        card: "0 1px 2px 0 rgb(14 17 51 / 0.04), 0 8px 24px -4px rgb(14 17 51 / 0.10)",
      },
    },
  },
  plugins: [],
};

import boundaries from "eslint-plugin-boundaries";

/** @type {import("eslint").Linter.Config[]} */
export default [
  {
    plugins: {
      boundaries,
    },
    settings: {
      "boundaries/elements": [
        { type: "feature", pattern: "features/*" },
        { type: "ui", pattern: "components/ui" },
        { type: "generated", pattern: "types/generated" },
      ],
    },
    rules: {
      "boundaries/element-types": [
        "error",
        {
          default: "allow",
          rules: [{ from: "feature", disallow: ["feature"] }],
        },
      ],
    },
  },
];

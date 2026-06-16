import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '..')

const files = {
  panel: readFileSync(resolve(root, 'src/components/AgentTracePanel.vue'), 'utf8'),
  runsView: readFileSync(resolve(root, 'src/views/AgentRunsView.vue'), 'utf8'),
}

const checks = [
  {
    name: 'verified status label',
    file: 'panel',
    patterns: ["case 'VERIFIED'", "return 'success'"],
  },
  {
    name: 'insufficient evidence review state',
    file: 'panel',
    patterns: ["verificationStatus.value === 'INSUFFICIENT_EVIDENCE'", 'needsManualReview'],
  },
  {
    name: 'manual review state',
    file: 'panel',
    patterns: ["verificationStatus.value === 'NEEDS_REVIEW'", 'reviewTarget'],
  },
  {
    name: 'partial fallback state',
    file: 'panel',
    patterns: ["props.run?.status === 'PARTIAL'", 'hasFallback'],
  },
  {
    name: 'failed step fallback state',
    file: 'panel',
    patterns: ["step.status === 'FAILED'", 'failedSteps.value.length'],
  },
  {
    name: 'evidence and confidence display',
    file: 'panel',
    patterns: ['finalEvidenceSnapshot', 'evidenceCount(step)', 'formatPercent(confidence)'],
  },
  {
    name: 'run list status filters',
    file: 'runsView',
    patterns: ["value: 'VERIFIED'", "value: 'INSUFFICIENT_EVIDENCE'", "value: 'NEEDS_REVIEW'", "value: 'PARTIAL'"],
  },
]

const failures = checks.flatMap((check) => {
  const content = files[check.file]
  return check.patterns
    .filter((pattern) => !content.includes(pattern))
    .map((pattern) => `${check.name}: missing ${pattern}`)
})

if (failures.length) {
  console.error('Agent trace state coverage check failed:')
  for (const failure of failures) {
    console.error(`- ${failure}`)
  }
  process.exit(1)
}

console.log(`Agent trace state coverage checks passed (${checks.length} groups).`)

import { useEffect, useRef } from 'react'
import * as d3 from 'd3'
import type { ClassInfo } from '../../api/migrateApi'
import { isClassEnabled, firstUnmigratedDep } from './classStatus'
import type { ClassStatus } from './classStatus'

interface Props {
  classes: ClassInfo[]
  dependencyGraph: Record<string, string[]>
  statuses: Record<string, ClassStatus>
  onMigrate: (className: string) => void
}

interface GraphNode extends d3.SimulationNodeDatum {
  id: string
  complexity: string
  status: ClassStatus
  enabled: boolean
  blockedBy: string | null
}

interface GraphLink extends d3.SimulationLinkDatum<GraphNode> {
  source: string | GraphNode
  target: string | GraphNode
}

const COMPLEXITY_COLORS: Record<string, string> = {
  LOW: '#2ecc71',
  MEDIUM: '#e5a00d',
  HIGH: '#e74c3c',
}

function getNodeFill(d: GraphNode): string {
  if (d.status === 'Complete') return '#4CAF50'
  if (d.status === 'PR Raised') return '#E5A00D'
  if (d.status === 'In Progress') return '#e5a00d'
  if (d.status === 'Pending' && !d.enabled) return '#555'
  return COMPLEXITY_COLORS[d.complexity] || '#45474e'
}

function getNodeFillOpacity(d: GraphNode): number {
  if (d.status === 'Complete' || d.status === 'PR Raised') return 0.85
  if (d.status === 'In Progress') return 0.5
  if (d.status === 'Pending' && !d.enabled) return 0.3
  return 0.2
}

function getNodeStroke(d: GraphNode): string {
  if (d.status === 'Complete') return '#4CAF50'
  if (d.status === 'PR Raised') return '#E5A00D'
  if (d.status === 'In Progress') return '#e5a00d'
  if (d.status === 'Pending' && !d.enabled) return '#555'
  return '#45474e'
}

function getNodeStrokeWidth(d: GraphNode): number {
  if (d.status === 'Complete' || d.status === 'PR Raised') return 3
  return 2
}

function getCursor(d: GraphNode): string {
  if (d.status === 'Pending' && d.enabled) return 'pointer'
  if (d.status === 'Pending' && !d.enabled) return 'not-allowed'
  return 'default'
}

function getTooltip(d: GraphNode): string {
  if (d.status === 'Pending' && d.enabled) return `Click to migrate ${d.id}`
  if (d.status === 'Pending' && !d.enabled && d.blockedBy)
    return `${d.id} \u2014 migrate ${d.blockedBy} first`
  return `${d.id} \u2014 ${d.status}`
}

export function DependencyGraph({ classes, dependencyGraph, statuses, onMigrate }: Props) {
  const svgRef = useRef<SVGSVGElement>(null)

  useEffect(() => {
    if (!svgRef.current || classes.length === 0) return

    const svg = d3.select(svgRef.current)
    svg.selectAll('*').remove()

    const width = svgRef.current.clientWidth || 600
    const height = 300

    svg.attr('viewBox', `0 0 ${width} ${height}`)

    const nodes: GraphNode[] = classes.map((c) => ({
      id: c.name,
      complexity: c.complexity,
      status: statuses[c.name] || 'Pending',
      enabled: isClassEnabled(c.name, dependencyGraph, statuses),
      blockedBy: firstUnmigratedDep(c.name, dependencyGraph, statuses),
    }))

    const links: GraphLink[] = []
    for (const [target, deps] of Object.entries(dependencyGraph)) {
      for (const source of deps) {
        if (classes.some((c) => c.name === source)) {
          links.push({ source, target })
        }
      }
    }

    const simulation = d3
      .forceSimulation(nodes)
      .force(
        'link',
        d3
          .forceLink<GraphNode, GraphLink>(links)
          .id((d) => d.id)
          .distance(120),
      )
      .force('charge', d3.forceManyBody().strength(-300))
      .force('center', d3.forceCenter(width / 2, height / 2))
      .force('collision', d3.forceCollide(45))

    const defs = svg.append('defs')

    // Arrow marker
    defs
      .append('marker')
      .attr('id', 'dep-arrow')
      .attr('viewBox', '0 -5 10 10')
      .attr('refX', 28)
      .attr('refY', 0)
      .attr('markerWidth', 8)
      .attr('markerHeight', 8)
      .attr('orient', 'auto')
      .append('path')
      .attr('d', 'M0,-5L10,0L0,5')
      .attr('fill', '#b0b3b8')

    const link = svg
      .append('g')
      .selectAll('line')
      .data(links)
      .join('line')
      .attr('stroke', '#7a7d83')
      .attr('stroke-width', 2)
      .attr('marker-end', 'url(#dep-arrow)')

    const nodeGroup = svg
      .append('g')
      .selectAll<SVGGElement, GraphNode>('g')
      .data(nodes)
      .join('g')
      .style('cursor', (d) => getCursor(d))
      .on('click', (_event, d) => {
        if (d.status === 'Pending' && d.enabled) {
          onMigrate(d.id)
        }
      })

    // Tooltip
    nodeGroup.append('title').text((d) => getTooltip(d))

    nodeGroup.call(
      d3
        .drag<SVGGElement, GraphNode>()
        .on('start', (event, d) => {
          if (!event.active) simulation.alphaTarget(0.3).restart()
          d.fx = d.x
          d.fy = d.y
        })
        .on('drag', (event, d) => {
          d.fx = event.x
          d.fy = event.y
        })
        .on('end', (event, d) => {
          if (!event.active) simulation.alphaTarget(0)
          d.fx = null
          d.fy = null
        }),
    )

    // Pulse ring for enabled Pending nodes
    nodeGroup
      .filter((d) => d.status === 'Pending' && d.enabled)
      .append('circle')
      .attr('r', 22)
      .attr('fill', 'none')
      .attr('stroke', (d) => COMPLEXITY_COLORS[d.complexity] || '#45474e')
      .attr('stroke-width', 1.5)
      .attr('stroke-opacity', 0.6)
      .attr('class', 'dep-graph-pulse')

    // Pulse ring for In Progress nodes (amber)
    nodeGroup
      .filter((d) => d.status === 'In Progress')
      .append('circle')
      .attr('r', 22)
      .attr('fill', 'none')
      .attr('stroke', '#e5a00d')
      .attr('stroke-width', 1.5)
      .attr('stroke-opacity', 0.6)
      .attr('class', 'dep-graph-pulse')

    // Node circle
    nodeGroup
      .append('circle')
      .attr('r', 22)
      .attr('fill', (d) => getNodeFill(d))
      .attr('fill-opacity', (d) => getNodeFillOpacity(d))
      .attr('stroke', (d) => getNodeStroke(d))
      .attr('stroke-width', (d) => getNodeStrokeWidth(d))

    // Inner dot for completed / PR Raised
    nodeGroup
      .filter((d) => d.status === 'Complete' || d.status === 'PR Raised')
      .append('circle')
      .attr('r', 6)
      .attr('fill', (d) => (d.status === 'Complete' ? '#fff' : '#fff'))

    // Node label
    nodeGroup
      .append('text')
      .text((d) => d.id)
      .attr('text-anchor', 'middle')
      .attr('dy', 36)
      .attr('fill', (d) => (d.status === 'Pending' && !d.enabled ? '#666' : '#b0b3b8'))
      .attr('font-size', '11px')
      .attr('font-weight', '600')
      .attr('font-family', "'JetBrains Mono', 'Fira Code', monospace")

    simulation.on('tick', () => {
      // Keep nodes within bounds
      nodes.forEach((d) => {
        d.x = Math.max(40, Math.min(width - 40, d.x!))
        d.y = Math.max(40, Math.min(height - 40, d.y!))
      })

      link
        .attr('x1', (d) => (d.source as GraphNode).x!)
        .attr('y1', (d) => (d.source as GraphNode).y!)
        .attr('x2', (d) => (d.target as GraphNode).x!)
        .attr('y2', (d) => (d.target as GraphNode).y!)

      nodeGroup.attr('transform', (d) => `translate(${d.x},${d.y})`)
    })

    return () => {
      simulation.stop()
    }
  }, [classes, dependencyGraph, statuses, onMigrate])

  return (
    <div className="dep-graph-container" data-testid="dependency-graph">
      <h3 className="dep-graph-title">Dependency Graph</h3>
      <div className="dep-graph-legend">
        <span className="dep-graph-legend-item">
          <span className="dep-graph-dot" style={{ background: '#2ecc71' }} /> LOW
        </span>
        <span className="dep-graph-legend-item">
          <span className="dep-graph-dot" style={{ background: '#e5a00d' }} /> MEDIUM
        </span>
        <span className="dep-graph-legend-item">
          <span className="dep-graph-dot" style={{ background: '#e74c3c' }} /> HIGH
        </span>
      </div>
      <svg ref={svgRef} className="dep-graph-svg" />
      <p className="dep-graph-hint">Click a class node to start migration</p>
    </div>
  )
}

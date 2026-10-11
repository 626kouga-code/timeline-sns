import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Icon } from './Icon'

describe('Icon', () => {
  it('装飾として読み上げない SVG を出す', () => {
    const { container } = render(<Icon name="heart" />)

    const svg = container.querySelector('svg')
    expect(svg).toHaveAttribute('aria-hidden', 'true')
    expect(svg).toHaveAttribute('fill', 'none')
    expect(svg).toHaveClass('size-5')
    expect(svg?.querySelector('path')).not.toBeNull()
  })

  it('filled なら塗りつぶし、className で大きさを変えられる', () => {
    const { container } = render(<Icon name="heart" filled className="size-6" />)

    expect(container.querySelector('svg')).toHaveAttribute('fill', 'currentColor')
    expect(container.querySelector('svg')).toHaveClass('size-6')
  })
})

import { useOutletContext } from 'react-router'

/** Layout が子の画面に渡す操作 */
export interface LayoutContext {
  openCompose: () => void
}

export const useLayout = () => useOutletContext<LayoutContext>()

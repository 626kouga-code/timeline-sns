const shapes = {
  home: <path d="M3 11l9-8 9 8M5 10v10h5v-6h4v6h5V10" />,
  globe: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18M12 3c3 3.5 3 14.5 0 18M12 3c-3 3.5-3 14.5 0 18" />
    </>
  ),
  bell: <path d="M6 16v-5a6 6 0 1 1 12 0v5l2 2H4l2-2zM10 20a2 2 0 0 0 4 0" />,
  user: (
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c0-4 4-6 8-6s8 2 8 6" />
    </>
  ),
  settings: <path d="M4 6h16M4 12h16M4 18h16" />,
  shield: <path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z" />,
  heart: <path d="M12 20s-7-4.5-9-9c-1.3-3 .7-6.5 4-6.5 2 0 3.5 1 5 3 1.5-2 3-3 5-3 3.3 0 5.3 3.5 4 6.5-2 4.5-9 9-9 9z" />,
  comment: <path d="M4 5h16v11H9l-5 4V5z" />,
  dots: (
    <>
      <circle cx="5" cy="12" r="1.2" />
      <circle cx="12" cy="12" r="1.2" />
      <circle cx="19" cy="12" r="1.2" />
    </>
  ),
  image: (
    <>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <circle cx="8.5" cy="10" r="1.5" />
      <path d="M21 16l-5-5-9 8" />
    </>
  ),
  x: <path d="M6 6l12 12M18 6L6 18" />,
  back: <path d="M15 5l-7 7 7 7" />,
  login: <path d="M10 17l5-5-5-5M15 12H3M14 4h5a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-5" />,
  logout: <path d="M14 7l5 5-5 5M19 12H8M10 4H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h5" />,
  pen: <path d="M4 20l4-1 11-11-3-3L5 16l-1 4z" />,
  flag: <path d="M5 21V4h11l-2 4 2 4H5" />,
  trash: <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13" />,
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <path d="M20 20l-4-4" />
    </>
  ),
  ban: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M5.6 5.6l12.8 12.8" />
    </>
  ),
} as const

export type IconName = keyof typeof shapes

interface Props {
  name: IconName
  className?: string
  filled?: boolean
}

export function Icon({ name, className = 'size-5', filled = false }: Props) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={className}
      fill={filled ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {shapes[name]}
    </svg>
  )
}

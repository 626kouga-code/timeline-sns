import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { UserSummary } from '../api/types'
import { renderWithProviders, user } from '../test/utils'
import { UserListItem } from './UserListItem'

const hanako: UserSummary = {
  id: 'u2',
  handle: 'hanako',
  displayName: '花子',
  bio: 'よろしく\nお願いします',
  avatarUrl: null,
  following: false,
  followedBy: true,
}

describe('UserListItem', () => {
  it('表示名・ユーザーID・自己紹介・フォローボタンを出す', () => {
    renderWithProviders(<UserListItem user={hanako} />, { me: user })

    expect(screen.getByText('花子')).toBeInTheDocument()
    expect(screen.getByText(/@hanako/)).toBeInTheDocument()
    expect(screen.getByText(/よろしく/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '@hanako をフォロー' })).toBeInTheDocument()
  })

  it('相手にフォローされていれば、その旨を出す', () => {
    renderWithProviders(<UserListItem user={hanako} />, { me: user })

    expect(screen.getByText('フォローされています')).toBeInTheDocument()
  })

  it('ゲストには「フォローされています」を出さない', () => {
    renderWithProviders(<UserListItem user={hanako} />)

    expect(screen.queryByText('フォローされています')).not.toBeInTheDocument()
  })

  it('自分の行にはフォローボタンも「フォローされています」も出さない', () => {
    renderWithProviders(<UserListItem user={{ ...hanako, id: user.id, handle: user.handle }} />, { me: user })

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByText('フォローされています')).not.toBeInTheDocument()
  })

  it('自己紹介が空なら出さない', () => {
    renderWithProviders(<UserListItem user={{ ...hanako, bio: '' }} />)

    expect(screen.queryByText(/よろしく/)).not.toBeInTheDocument()
  })

  it('行を押すとプロフィールへ移る', async () => {
    renderWithProviders(<UserListItem user={hanako} />)

    await userEvent.click(screen.getByText('花子'))

    expect(screen.getByTestId('location')).toHaveTextContent('/users/hanako')
  })
})

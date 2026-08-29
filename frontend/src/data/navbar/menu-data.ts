import team1 from 'assets/images/team/team-1.jpg';
import team2 from 'assets/images/team/team-2.jpg';
import team3 from 'assets/images/team/team-3.jpg';
import team4 from 'assets/images/team/team-4.jpg';
import team5 from 'assets/images/team/team-5.jpg';
import { uniqueId } from 'lodash';
interface IProfileOptions {
  id: string;
  title: string;
  icon: string;
}
export const profileOptions: IProfileOptions[] = [
  { id: uniqueId(), title: 'My Profile', icon: 'fa-regular:user' },
  { id: uniqueId(), title: 'My Account', icon: 'fa6-regular:envelope' },
  { id: uniqueId(), title: 'My Tasks', icon: 'fa-solid:tasks' },
];

interface INotification {
  id: string;
  avatar: string;
  title: string;
  subtitle: string;
}

export const notificationOptions: INotification[] = [
  { id: uniqueId(), avatar: team1, title: 'Alex Joined the Team!', subtitle: 'Welcome him aboard' },
  {
    id: uniqueId(),
    avatar: team2,
    title: 'Meeting Reminder',
    subtitle: 'Don’t forget the 3 PM meeting',
  },
  { id: uniqueId(), avatar: team3, title: 'Invoice Paid', subtitle: 'Your invoice has been paid' },
  { id: uniqueId(), avatar: team4, title: 'Sara completed tasks', subtitle: 'Review her work' },
  { id: uniqueId(), avatar: team5, title: 'Anna Joined the Team!', subtitle: 'Say hello to her' },
  {
    id: uniqueId(),
    avatar: team1,
    title: 'New Comment on Post',
    subtitle: 'John commented on your post',
  },
  {
    id: uniqueId(),
    avatar: team2,
    title: 'Project Deadline Approaching',
    subtitle:
      'Submit your work by end of day lorem afk al jafl jala ;aej d jas sgjslkdfgj sdg jg lkdjsg lkdjs',
  },
  {
    id: uniqueId(),
    avatar: team3,
    title: 'Emma completed tasks',
    subtitle: 'Give her new assignments',
  },
];

export const messageOptions: INotification[] = [
  { id: uniqueId(), avatar: team1, title: 'Alex Joined the Team!', subtitle: 'Welcome him aboard' },
  {
    id: uniqueId(),
    avatar: team2,
    title: 'Meeting Reminder',
    subtitle: 'Don’t forget the 3 PM meeting',
  },
  { id: uniqueId(), avatar: team3, title: 'Invoice Paid', subtitle: 'Your invoice has been paid' },
  { id: uniqueId(), avatar: team4, title: 'Sara completed tasks', subtitle: 'Review her work' },
  { id: uniqueId(), avatar: team5, title: 'Anna Joined the Team!', subtitle: 'Say hello to her' },
  {
    id: uniqueId(),
    avatar: team1,
    title: 'New Comment on Post',
    subtitle: 'John commented on your post',
  },
  {
    id: uniqueId(),
    avatar: team2,
    title: 'Project Deadline Approaching',
    subtitle:
      'Submit your work by end of day lorem afk al jafl jala ;aej d jas sgjslkdfgj sdg jg lkdjsg lkdjs',
  },
  {
    id: uniqueId(),
    avatar: team3,
    title: 'Emma completed tasks',
    subtitle: 'Give her new assignments',
  },
];
